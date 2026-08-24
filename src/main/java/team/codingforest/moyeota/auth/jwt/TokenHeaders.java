package team.codingforest.moyeota.auth.jwt;

/*
토큰을 실어 보내는 응답 헤더의 이름.

AuthController(헤더를 넣는 쪽)와 SecurityConfig(CORS에서 이 헤더를 노출 목록에 올리는 쪽)가
같은 문자열을 써야 해서 여기 모아둔다.
한쪽만 고치면 프론트에서 헤더가 null로 읽히는데, 그때 원인을 찾기가 상당히 번거롭다.
*/
public final class TokenHeaders {

    /*
    access 토큰을 담는 헤더. 값의 모양은 "Bearer eyJhbGciOi..." 이다.

    요청에 쓰는 Authorization 헤더와 같은 이름을 일부러 골랐다.
    JWTFilter가 요청에서 읽는 형식이 "Authorization: Bearer <token>" 이므로,
    프론트는 로그인 응답의 이 헤더 값을 그대로 다음 요청의 Authorization에 넣으면 된다.
    (이름이 다르면 프론트가 "Bearer "를 떼었다 붙였다 하는 코드를 들고 있어야 한다)
    */
    public static final String ACCESS = "Authorization";

    //access 헤더 값 앞에 붙는 접두사. JWTFilter가 요청에서 떼어내는 값과 같아야 한다.
    public static final String BEARER_PREFIX = "Bearer ";

    /*
    refresh 토큰을 담는 헤더.

    Bearer를 붙이지 않는다. Bearer는 "이 토큰으로 지금 인증하라"는 뜻인데
    refresh는 인증에 쓰는 토큰이 아니라 재발급에만 쓰는 값이라서다.
    */
    public static final String REFRESH = "Refresh-Token";

    //상수만 모아둔 클래스라 인스턴스를 만들 일이 없다.
    private TokenHeaders() {
    }
}
