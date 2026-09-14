# 인터셉터 기반 사용자 신원(userId) 전달 구조

토큰에서 꺼낸 신원을 컨트롤러·서비스 등 다른 기능에 전달하는 과정을
PAAR(Problem → Approach → Action → Result) 순서로 정리한 문서다.

## 전체 흐름

```
클라이언트 (Authorization: Bearer <access>)
  → JWTFilter (서블릿 필터)         : 토큰 검증, publicId를 SecurityContext에 저장
  → Spring Security 인가 단계        : 보호 경로면 인증 없을 때 401
  → DispatcherServlet
  → UserInterceptor.preHandle       : publicId → DB 조회 → userId → UserContext(ThreadLocal)에 저장
  → 컨트롤러 / 서비스               : UserContext.get()으로 꺼내 씀
  → UserInterceptor.afterCompletion : UserContext.clear()
```

핵심은 **토큰에는 userId(PK)가 들어있지 않다**는 점이다.
토큰의 sub에는 publicId(UUID)만 들어있고, userId는 인터셉터가 DB를 한 번 조회해서 만들어낸다.

관련 파일

| 역할 | 파일 |
|---|---|
| 토큰 검증 | `src/main/java/team/codingforest/moyeota/auth/jwt/JWTFilter.java` |
| 인가 규칙 | `src/main/java/team/codingforest/moyeota/auth/config/SecurityConfig.java` |
| 인터셉터·리졸버 등록 | `src/main/java/team/codingforest/moyeota/auth/config/WebConfig.java` |
| 신원 변환 | `src/main/java/team/codingforest/moyeota/auth/web/UserInterceptor.java` |
| 신원 보관 | `src/main/java/team/codingforest/moyeota/auth/web/UserContext.java` |
| 전달 값 | `src/main/java/team/codingforest/moyeota/auth/web/LoginUser.java` |
| 소비자 | `src/main/java/team/codingforest/moyeota/auth/controller/MypageController.java` |
| 대안(꺼짐) | `src/main/java/team/codingforest/moyeota/auth/web/CurrentUserArgumentResolver.java` |

---

## P. Problem: 인터셉터가 풀어야 했던 문제

- **토큰에는 userId가 없다.** JWT의 sub에는 publicId(UUID)만 들어있다. 하지만 서비스 계층과 다른 기능들이 실제로 필요로 하는 값은 DB PK인 userId다. 누군가는 publicId를 userId로 바꿔야 한다.
- **변환 코드가 컨트롤러마다 복사될 위험이 있었다.** 토큰 → publicId → DB 조회 → userId 과정을 API마다 적으면 한 곳만 빠뜨려도 그 자리가 보안 구멍이 된다.
- **신원의 출처를 토큰 하나로 고정해야 했다.** 프론트가 보낸 userId 파라미터를 받는 순간 위조가 가능해진다. 수정 API에서는 남의 데이터를 고칠 수 있는 문제로 이어진다.
- **파라미터로 신원을 넘기지 않고도 어느 계층에서든 꺼낼 수 있어야 했다.** 컨트롤러가 서비스로, 서비스가 또 다른 서비스로 userId를 계속 인자로 전달하는 구조는 시그니처를 오염시킨다.

## A. Approach: 어떤 방식으로 접근했나

- **검증과 변환의 책임을 분리했다.** 토큰의 서명·만료·category 검증은 JWTFilter 한 곳에만 둔다. 인터셉터는 검증 결과를 SecurityContext에서 읽기만 하고 토큰을 다시 파싱하지 않는다. 규칙이 두 곳으로 흩어져 한쪽만 고치는 사고를 막기 위해서다.
- **변환 지점을 컨트롤러 앞 단계인 인터셉터 하나로 모았다.** 요청이 컨트롤러에 닿기 전에 preHandle이 publicId → userId 변환을 끝내둔다. 이후 계층은 변환을 몰라도 된다.
- **전달 수단으로 ThreadLocal 기반의 UserContext를 택했다.** 요청 하나는 스레드 하나가 처음부터 끝까지 처리한다는 서블릿 모델에 기대어, 스레드에 신원을 매달아두면 어느 계층에서든 같은 값을 본다.
- **인터셉터에서는 401을 던지지 않기로 했다.** 인터셉터는 permitAll 경로에서도 돈다. 여기서 401을 던지면 열려 있어야 할 API가 막힌다. 보호 경로의 차단은 Spring Security 인가 단계가 이미 하므로, 인터셉터는 인증이 없으면 조용히 비운 채 통과시킨다.
- **전달 값은 User 엔티티가 아니라 LoginUser 레코드로 제한했다.** userId와 publicId 두 값만 담는다. 영속 객체가 요청 내내 떠돌면 지연 로딩이나 트랜잭션 밖 변경 누수가 생긴다.

## A. Action: 실제로 무엇을 구현했나

### 등록 (`WebConfig`)

- UserInterceptor를 `/**`에 등록하고 `/api/v1/auth/**`만 제외했다. 로그인·재발급 경로는 토큰 없이 호출되어 SecurityContext가 비어있으므로 돌 이유가 없다.
- 같은 역할의 `@CurrentUser` 리졸버는 등록 줄을 주석 처리해 껐다. 두 방식을 동시에 켜면 DB 조회가 두 번 일어나기 때문이다.

### preHandle (`UserInterceptor`)

1. SecurityContextHolder에서 Authentication을 읽는다.
2. 인증이 없거나 principal이 CustomOAuth2User가 아니면 아무것도 하지 않고 true를 반환한다. 익명 사용자의 principal은 문자열이라 여기서 자연히 걸러진다.
3. principal의 `getPublicId()`로 publicId 문자열을 꺼낸다.
4. `UUID.fromString`으로 변환한다. 옛 형식 토큰이면 예외가 나는데, 이 경우도 비운 채 통과시킨다.
5. `userRepository.findByPublicId`로 DB를 한 번 조회한다. 사용자가 있으면 `LoginUser(userId, publicId)`를 만들어 `UserContext.set`에 넣는다. 탈퇴 등으로 없으면 역시 비운 채 통과시킨다.

### 보관 (`UserContext`)

- 정적 ThreadLocal 하나에 LoginUser를 담는 유틸 클래스다. `set`, `get`, `clear` 세 메서드만 있다.
- `get`은 인증되지 않은 요청에서 null일 수 있고, 부르는 쪽이 null을 감안한다.

### afterCompletion (`UserInterceptor`)

- 예외 발생 여부와 무관하게 `UserContext.clear()`로 ThreadLocal을 제거한다.
- 톰캣이 스레드를 풀에서 재사용하므로 비우지 않으면 다음 요청이 이전 사용자의 신원을 물려받는다. 이 구조에서 가장 중요한 안전장치다.

### 소비 (`MypageController`)

- `currentUser()` 헬퍼가 `UserContext.get()`을 호출하고, null이면 401을 던진다. permitAll 경로에 토큰 없이 들어온 경우를 위한 마지막 방어다.
- 본체가 필요한 마이페이지는 userId로 한 번 더 조회한 뒤 그 User를 UserService에 넘긴다. 서비스는 UserContext를 직접 보지 않는다.
- 프론트가 보낸 userId 파라미터는 어디에서도 읽지 않는다.

## R. Result: 무엇이 달라졌나

- **신원 변환이 한 곳에만 존재한다.** 새 API를 추가할 때 토큰 처리 코드를 적을 필요가 없고, 빠뜨려서 생기는 구멍도 없다.
- **신원의 출처가 토큰으로 고정됐다.** 어떤 컨트롤러도 프론트가 보낸 userId를 읽지 않는다. `?userId=2`를 붙여도 남의 데이터에 닿을 방법이 없다.
- **계층 간 시그니처가 깨끗해졌다.** 컨트롤러나 서비스가 userId를 인자로 계속 넘기지 않아도 어디서든 `UserContext.get()`으로 꺼낸다.
- **검증 규칙이 JWTFilter 한 곳에만 남았다.** 인터셉터는 읽기만 하므로 토큰 정책이 바뀌어도 고칠 곳은 필터 하나다.
- **비용은 보호 경로마다 DB 조회 한 번이다.** publicId → userId 매핑은 바뀌지 않는 값(`User.publicId`가 `updatable=false`)이라 부담이 커지면 캐시를 얹을 수 있다.

### 남은 한계

- ThreadLocal은 스레드 경계를 넘지 못한다. `@Async`나 별도 스레드풀, CompletableFuture 등에서는 `UserContext.get()`이 null이 되므로, 비동기 호출 전에 값을 꺼내 인자로 넘겨야 한다.
- 현재 소비자는 MypageController 하나다. 다른 모듈이 붙을 때 이 구조를 그대로 쓸지, 꺼둔 `@CurrentUser` 리졸버 방식과 비교해 한쪽으로 통일할지는 정해야 할 사항이다.

---

## 부록: userId와 publicId를 둘 다 들고 다니는 이유

`LoginUser`는 두 값만 담는 레코드다.

| 값 | 용도 |
|---|---|
| `userId` | DB PK. 다른 서비스가 FK 조인이나 조회에 쓰는 값 |
| `publicId` | 응답이나 로그에 노출하는 값. 순번인 PK를 노출하면 다른 사용자를 추측할 수 있어 밖으로는 UUID만 내보낸다 |

## 부록: 대안으로 남아있는 `@CurrentUser` 리졸버 (현재 꺼짐)

`CurrentUserArgumentResolver`는 같은 변환(토큰 → publicId → User)을 하되, 컨트롤러 파라미터에 `@CurrentUser`를 붙인 곳에서만 동작하는 방식이다.

| 비교 항목 | 인터셉터 방식 (현재) | 리졸버 방식 (꺼짐) |
|---|---|---|
| DB 조회 시점 | 매칭된 모든 요청 | 어노테이션을 붙인 API만 |
| 인증 없을 때 | 조용히 비운 채 통과, 컨트롤러가 null 확인 | 401을 던짐 |
| 전달 방식 | `UserContext.get()` 정적 호출 | 컨트롤러 파라미터 주입 |
| 받을 수 있는 타입 | `LoginUser` | `LoginUser` 또는 `User` |

둘을 동시에 켜면 `@CurrentUser`를 쓰는 API에서 조회가 두 번 일어나므로 한쪽만 켜서 쓴다.
