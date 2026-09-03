package team.codingforest.moyeota.driver.interfaces.auth;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.driver.api.CurrentDriver;
import team.codingforest.moyeota.driver.domain.Drivers;
import team.codingforest.moyeota.driver.domain.exception.DriverErrorCode;

@Component
@RequiredArgsConstructor
public class CurrentDriverArgumentResolver implements HandlerMethodArgumentResolver {
    private final Drivers drivers;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentDriver.class) && parameter.getParameterType().equals(Long.class);
    }

    @Override
    public Object resolveArgument(@NonNull MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  @NonNull NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new BusinessException(DriverErrorCode.UNAUTHORIZED);
        }

        // 토큰의 유저 → 기사 자격 확인 → 내부 driverId 주입
        return drivers.findByUserId(principal.userId())
                .orElseThrow(() -> new BusinessException(DriverErrorCode.DRIVER_NOT_REGISTERED))
                .getId();
    }
}