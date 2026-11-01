# 유닛 테스트 작성 가이드

멘토님 샘플(maestro3)의 테스트 방식을 정리한 문서입니다. 이 레포의 테스트는 아래 규칙을 따릅니다.

## 원칙

1. **유닛 테스트는 한 계층만 검증한다.** 테스트 대상이 의존하는 객체는 전부 mock으로 끊는다.
   - mock은 "이 값이 들어오면 무조건 이걸 반환해"라는 선언이다. 하위 계층의 내부 로직은 타지 않는다.
   - 하위 계층이 맞는지는 하위 계층의 유닛 테스트가 따로 보장한다.
2. **이유는 조합 폭발.** 상위 분기 100개 × 하위 분기 100개를 한꺼번에 테스트하지 않는다. 계층마다 100개씩만 테스트한다.
3. **예외 케이스를 빠짐없이 고민한다.** 유닛 테스트의 가치는 촘촘함에서 나온다. 성공 케이스뿐 아니라 실패, 경계값, null, 예외 발생 케이스를 작성한다.
4. **통합 테스트(`@SpringBootTest`로 전체 컨텍스트를 띄우거나 실제 HTTP/DB/외부 API를 호출하는 테스트)는 빌드에 넣지 않는다.**
   - 느리고(빌드 수십 분), 외부 컴포넌트 장애로 배포가 막히고, 로직이 바뀔 때마다 따라 고쳐야 하고, 외부 의존(토큰 발급 등)을 다 담을 수 없기 때문이다.
   - 실제 환경 연결 확인은 배포 후 외부에서 주기적으로 한다(크론 + 스크립트, Newman 등 → 실패 시 Slack 알림).

## 폴더 / 이름 규칙

- 테스트는 기본 `src/test/java` 아래에 둔다. `unit/`, `integration/`처럼 폴더를 따로 나누지 않는다.
- 테스트 패키지는 대상 클래스와 같은 패키지로 맞춘다.
- 클래스 이름: 서비스는 `XxxServiceUnitTest`, 컨트롤러(API)는 `XxxControllerUnitTest`.
- 각 테스트 메서드에 `@DisplayName("메서드명 - 성공/실패 상황")`을 붙인다.
- 메서드 본문은 `settings` / `test` / `verify` 세 블록 주석으로 나눈다.

## 템플릿 1: 서비스 유닛 테스트

대상 서비스는 진짜 객체, 대상이 의존하는 서비스/리포지토리는 전부 `@MockBean`.

```java
@ExtendWith(SpringExtension.class)
public class MaestroServiceUnitTest {

	@SpyBean
	private MaestroService maestroService;   // 테스트 대상
	@MockBean
	private BookService bookService;         // 의존성 → mock
	@MockBean
	private StudentService studentService;   // 의존성 → mock

	@DisplayName("registerBookByStudent - 성공")
	@Test
	void registerBookByStudent() {

		/*
		 * settings
		 */
		final String studentId = "TestStudent001";
		final String bookId = "TestBook001";

		doReturn(true).when(bookService).exist(eq(bookId));
		doReturn(true).when(studentService).exist(eq(studentId));

		/*
		 * test
		 */
		boolean success = maestroService.registerBookByStudent(studentId, bookId);

		/*
		 * verify
		 */
		assertThat(success).isTrue();
		verify(studentService).exist(eq(studentId));
		verify(bookService).exist(eq(bookId));
	}

	@DisplayName("registerBookByStudent - 학생이 없으면 실패")
	@Test
	void registerBookByStudent_noStudent() {

		/*
		 * settings
		 */
		doReturn(false).when(studentService).exist(eq("TestStudent001"));

		/*
		 * test
		 */
		boolean success = maestroService.registerBookByStudent("TestStudent001", "TestBook001");

		/*
		 * verify
		 */
		assertThat(success).isFalse();
		verify(bookService, never()).exist(any());   // && 단락 평가로 호출되지 않아야 함
	}
}
```

## 템플릿 2: 컨트롤러(API) 유닛 테스트 — MockMvc

`@WebMvcTest`로 웹 계층만 띄운다(실제 서버/DB 없음, 가상 서블릿). 컨트롤러가 의존하는 서비스는 `@MockBean`.

```java
@WebMvcTest(MaestroController.class)
public class MaestroControllerUnitTest {

	@Autowired
	private MockMvc mockMvc;
	@MockBean
	private MaestroService maestroService;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@DisplayName("POST /test/rental - 성공")
	@Test
	void rental() throws Exception {

		/*
		 * settings
		 */
		doReturn(true).when(maestroService)
			.registerBookByStudent(eq("TestStudent001"), eq("TestBook001"));

		final String requestJson = objectMapper.writeValueAsString(
			new RentalRequestBody("TestBook001", "TestStudent001"));

		/*
		 * test
		 */
		mockMvc.perform(post("/test/rental").contentType(MediaType.APPLICATION_JSON).content(requestJson))
			.andExpect(status().isOk())
			.andExpect(content().string("true"));

		/*
		 * verify
		 */
		verify(maestroService).registerBookByStudent(eq("TestStudent001"), eq("TestBook001"));
	}
}
```

## 하지 말 것

- 빌드되는 테스트에서 `@SpringBootTest`로 전체 컨텍스트를 띄우기
- `@Autowired`로 실제 서비스를 주입해서 하위 계층까지 타고 들어가기
- `TestRestTemplate`, `WebTestClient` 등으로 실제 HTTP 호출하기
- 실제 DB, 외부 API, 다른 팀 서버에 의존하기
- 성공 케이스 하나만 작성하고 끝내기

## Spring Boot 버전별 주의

- **3.3 이하**: 위 템플릿 그대로 사용(`@MockBean`, `@SpyBean`).
- **3.4 이상**: `@MockBean`/`@SpyBean`이 deprecated. `@MockitoBean`(`org.springframework.test.context.bean.override.mockito`)으로 바꾼다.
  `@MockitoSpyBean`은 이미 등록된 빈이 있어야 하므로, 서비스 테스트는 `@ContextConfiguration(classes = 대상Service.class)`를 함께 붙이거나
  `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks` 방식으로 작성한다.
- **4.x**: `@WebMvcTest` 패키지/의존성 모듈이 바뀌었으니 해당 버전 문서를 확인한다.
