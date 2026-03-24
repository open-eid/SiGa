package ee.openeid.siga.auth.filter;

import lombok.NonNull;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.UrlHandlerFilter;

//TODO (SIGA-1283): Remove this Class when SiGa clients don't use URL-s with trailing slashes anymore.
@Configuration
public class TrailingSlashFilterConfiguration {

    @Bean
    FilterRegistrationBean<@NonNull UrlHandlerFilter> trailingSlashFilter() {
        UrlHandlerFilter filter = UrlHandlerFilter
                .trailingSlashHandler("/**")
                .intercept(req -> req.setAttribute("hmac.originalUri", req.getRequestURI()))
                .wrapRequest()
                .build();

        FilterRegistrationBean<@NonNull UrlHandlerFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }
}
