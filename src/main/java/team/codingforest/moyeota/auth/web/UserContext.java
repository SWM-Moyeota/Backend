package team.codingforest.moyeota.auth.web;

/*
"지금 요청한 사용자"를 요청 처리 내내 들고 다니는 자리.

인터셉터(UserInterceptor)가 preHandle에서 채우고 afterCompletion에서 비운다.
컨트롤러에 파라미터로 넘기지 않고도 서비스 계층 어디서든 UserContext.get()으로 꺼낼 수 있다.

[ThreadLocal을 쓰는 이유]
요청 하나는 스레드 하나가 처음부터 끝까지 처리하므로, 그 스레드에 값을 매달아두면
같은 요청 안에서는 어디서나 같은 값을 본다. 반대로 요청이 다르면 스레드가 달라 서로 안 섞인다.

[반드시 clear() 해야 하는 이유]
톰캣은 스레드를 풀에서 재사용한다. 비우지 않으면 다음 요청이 같은 스레드를 받았을 때
이전 사용자의 값을 그대로 보게 된다. 남의 신원으로 처리되는 사고다.
그래서 UserInterceptor.afterCompletion에서 예외 여부와 무관하게 항상 비운다.
*/
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    //인증되지 않은 요청(permitAll 경로 등)에서는 null일 수 있다. 부르는 쪽에서 null을 감안한다.
    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
