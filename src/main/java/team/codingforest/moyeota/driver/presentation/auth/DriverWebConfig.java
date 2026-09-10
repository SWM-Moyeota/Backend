package team.codingforest.moyeota.driver.presentation.auth;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import team.codingforest.moyeota.driver.api.CurrentDriver;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class DriverWebConfig implements WebMvcConfigurer {
    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentDriver.class);
    }

    private final CurrentDriverArgumentResolver currentDriverArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentDriverArgumentResolver);
    }
}
