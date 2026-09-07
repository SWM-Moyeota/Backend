package team.codingforest.moyeota.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import team.codingforest.moyeota.auth.web.CurrentUserArgumentResolver;
import team.codingforest.moyeota.auth.web.UserInterceptor;

import java.util.List;

/*
커스텀 ArgumentResolver와 인터셉터를 스프링 MVC에 등록한다.

만들어두기만 하면 동작하지 않는다. 여기서 등록해야 @CurrentUser 가 채워지고
UserInterceptor가 요청을 가로챈다.
등록을 빠뜨리면 컴파일도 되고 앱도 뜨는데 조용히 동작만 안 하므로
"왜 안 들어오지" 하기 좋은 자리다.
*/
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final UserInterceptor userInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        //[비활성화] 지금은 인터셉터(UserInterceptor + UserContext) 방식으로 신원을 전달한다.
        //@CurrentUser 리졸버는 나중에 두 방식을 비교하려고 코드만 남겨둔다.
        //다시 켜려면 아래 한 줄의 주석을 풀면 된다. 단, 인터셉터 방식과 동시에 켜면
        //@CurrentUser 를 쓰는 API가 DB를 두 번 조회하게 되니 한쪽만 켜서 쓴다.
        //resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userInterceptor)
                //모든 경로에서 돌되, 토큰 없이 호출되는 인증 API에서는 굳이 돌 필요가 없어 제외한다.
                //(JWTFilter도 이 경로는 건너뛰므로 어차피 SecurityContext가 비어 있다)
                .addPathPatterns("/**")
                .excludePathPatterns("/api/v1/auth/**");
    }
}
