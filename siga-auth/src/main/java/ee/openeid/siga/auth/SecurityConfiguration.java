package ee.openeid.siga.auth;

import ee.openeid.siga.auth.filter.MethodFilter;
import ee.openeid.siga.auth.filter.RequestDataVolumeFilter;
import ee.openeid.siga.auth.filter.event.SigaEventLoggingFilter;
import ee.openeid.siga.auth.filter.hmac.HmacAuthenticationFilter;
import ee.openeid.siga.auth.filter.hmac.HmacAuthenticationProvider;
import ee.openeid.siga.auth.filter.logging.ContainerIdForAccessLogFilter;
import ee.openeid.siga.auth.filter.logging.CorrelationIdForAccessLogFilter;
import ee.openeid.siga.auth.properties.SecurityConfigurationProperties;
import ee.openeid.siga.common.event.SigaEventLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableAutoConfiguration
@ComponentScan
@EnableWebSecurity
@EnableJpaRepositories
@EnableConfigurationProperties(SecurityConfigurationProperties.class)
@RequiredArgsConstructor
public class SecurityConfiguration {
    private static final RequestMatcher PUBLIC_URLS = new OrRequestMatcher(new AntPathRequestMatcher("/siga.wadl"), new AntPathRequestMatcher("/siga.xsd"),
            new AntPathRequestMatcher("/actuator/health"), new AntPathRequestMatcher("/actuator/heartbeat"), new AntPathRequestMatcher("/actuator/version"));
    private static final RequestMatcher PROTECTED_URLS = new NegatedRequestMatcher(PUBLIC_URLS);
    private final HmacAuthenticationProvider hmacAuthenticationProvider;
    private final MethodFilter methodFilter;
    private final SigaEventLoggingFilter eventsLoggingFilter;
    private final RequestDataVolumeFilter requestDataVolumeFilter;
    private final CorrelationIdForAccessLogFilter correlationIdForAccessLogFilter;
    private final ContainerIdForAccessLogFilter containerIdForAccessLogFilter;

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers(PUBLIC_URLS);
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.authenticationProvider(hmacAuthenticationProvider);
        return authenticationManagerBuilder.build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, HmacAuthenticationFilter hmacAuthenticationFilter) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(STATELESS));
        http.exceptionHandling(Customizer.withDefaults());
        http
                .addFilterBefore(hmacAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .addFilterBefore((servletRequest, servletResponse, filterChain) -> {
                    ContentCachingRequestWrapper cachingRequestWrapper = new ContentCachingRequestWrapper(servletRequest);
                    filterChain.doFilter(cachingRequestWrapper, servletResponse);
                }, HmacAuthenticationFilter.class)
                .addFilterAfter(methodFilter, BasicAuthenticationFilter.class)
                .addFilterAfter(requestDataVolumeFilter, SecurityContextHolderAwareRequestFilter.class)
                .addFilterAfter(eventsLoggingFilter, SecurityContextHolderAwareRequestFilter.class)
                .addFilterBefore(correlationIdForAccessLogFilter, MethodFilter.class)
                .addFilterAfter(containerIdForAccessLogFilter, MethodFilter.class);
        http.authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLIC_URLS).permitAll()
                    .requestMatchers(PROTECTED_URLS).authenticated()
                );
        http.csrf(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    HmacAuthenticationFilter authenticationFilter(SecurityConfigurationProperties configurationProperties,
                                                  SigaEventLogger sigaEventLogger, AuthenticationManager authenticationManager) {
        final HmacAuthenticationFilter filter = new HmacAuthenticationFilter(sigaEventLogger, PROTECTED_URLS, configurationProperties);
        filter.setAuthenticationManager(authenticationManager);
        return filter;
    }

    @Bean
    FilterRegistrationBean<HmacAuthenticationFilter> disableAutoRegistration(final HmacAuthenticationFilter filter) {
        final FilterRegistrationBean<HmacAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
