package ee.openeid.siga.auth;

import ee.openeid.siga.auth.filter.event.SigaEventsLoggingFilter;
import ee.openeid.siga.auth.filter.hmac.HmacAuthenticationFilter;
import ee.openeid.siga.auth.filter.hmac.HmacAuthenticationProvider;
import ee.openeid.siga.auth.properties.SecurityConfigurationProperties;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static lombok.AccessLevel.PRIVATE;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableAutoConfiguration
@ComponentScan
@EnableWebSecurity
@EnableJpaRepositories
@EnableConfigurationProperties(SecurityConfigurationProperties.class)
@FieldDefaults(level = PRIVATE)
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
    private static final RequestMatcher PUBLIC_URLS = new OrRequestMatcher(new AntPathRequestMatcher("/siga.wadl"));
    private static final RequestMatcher PROTECTED_URLS = new NegatedRequestMatcher(PUBLIC_URLS);

    @Autowired
    SecurityConfigurationProperties configurationProperties;

    @Autowired
    HmacAuthenticationProvider hmacAuthenticationProvider;

    @Autowired
    SigaEventsLoggingFilter eventsLoggingFilter;

    @Override
    public void configure(final WebSecurity web) {
        web.ignoring().requestMatchers(PUBLIC_URLS);
    }

    @Override
    protected void configure(final AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(hmacAuthenticationProvider);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .sessionManagement().sessionCreationPolicy(STATELESS)
                .and()
                .exceptionHandling()
                .and()
                .addFilterBefore(authenticationFilter(), AnonymousAuthenticationFilter.class)
                .addFilterBefore((servletRequest, servletResponse, filterChain) -> {
                    ContentCachingRequestWrapper cachingRequestWrapper =
                            new ContentCachingRequestWrapper(servletRequest);
                    filterChain.doFilter(cachingRequestWrapper, servletResponse);
                }, HmacAuthenticationFilter.class)
                .addFilterAfter(eventsLoggingFilter, SecurityContextHolderAwareRequestFilter.class)
                .authorizeRequests()
                .requestMatchers(PUBLIC_URLS).permitAll()
                .requestMatchers(PROTECTED_URLS).authenticated()
                .and()
                .csrf().disable()
                .formLogin().disable()
                .httpBasic().disable()
                .logout().disable();
    }

    @Bean
    HmacAuthenticationFilter authenticationFilter() throws Exception {
        final HmacAuthenticationFilter filter = new HmacAuthenticationFilter(PROTECTED_URLS,
                configurationProperties);
        filter.setAuthenticationManager(authenticationManager());
        return filter;
    }

    @Bean
    FilterRegistrationBean disableAutoRegistration(final HmacAuthenticationFilter filter) {
        final FilterRegistrationBean registration = new FilterRegistrationBean(filter);
        registration.setEnabled(false);
        return registration;
    }
}