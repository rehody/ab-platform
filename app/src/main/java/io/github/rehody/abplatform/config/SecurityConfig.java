package io.github.rehody.abplatform.config;

import io.github.rehody.abplatform.security.RequiresPlatformPermission;
import io.github.rehody.abplatform.web.security.PlatformActorAuthenticationConverter;
import io.github.rehody.abplatform.web.security.PlatformActorAuthenticationProvider;
import io.github.rehody.abplatform.web.security.PlatformPermissionAuthorizationManager;
import io.github.rehody.abplatform.web.security.PlatformSecurityExceptionHandler;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.Pointcuts;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableMethodSecurity(prePostEnabled = false)
public class SecurityConfig {

    private static final RequestMatcher PROTECTED_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern("/api/v1/**"), PathPatternRequestMatcher.pathPattern("/reports/**"));

    private static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern("/api/v1/assignments/**"),
            PathPatternRequestMatcher.pathPattern("/events/**"));

    private static final RequestMatcher AUTHENTICATION_ENDPOINTS =
            new AndRequestMatcher(PROTECTED_ENDPOINTS, new NegatedRequestMatcher(PUBLIC_ENDPOINTS));

    @Bean
    public AuthenticationManager authenticationManager(
            PlatformActorAuthenticationProvider platformActorAuthenticationProvider) {
        return new ProviderManager(platformActorAuthenticationProvider);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public AuthorizationManagerBeforeMethodInterceptor requiresPlatformPermissionMethodInterceptor(
            PlatformPermissionAuthorizationManager platformPermissionAuthorizationManager) {
        Pointcut pointcut = Pointcuts.union(
                AnnotationMatchingPointcut.forMethodAnnotation(RequiresPlatformPermission.class),
                AnnotationMatchingPointcut.forClassAnnotation(RequiresPlatformPermission.class));
        return new AuthorizationManagerBeforeMethodInterceptor(pointcut, platformPermissionAuthorizationManager);
    }

    @Bean
    public AuthenticationFilter platformActorAuthenticationFilter(
            AuthenticationManager authenticationManager,
            PlatformActorAuthenticationConverter platformActorAuthenticationConverter,
            PlatformSecurityExceptionHandler platformSecurityExceptionHandler) {
        AuthenticationFilter authenticationFilter =
                new AuthenticationFilter(authenticationManager, platformActorAuthenticationConverter);
        authenticationFilter.setRequestMatcher(AUTHENTICATION_ENDPOINTS);
        authenticationFilter.setFailureHandler(
                new AuthenticationEntryPointFailureHandler(platformSecurityExceptionHandler));
        authenticationFilter.setSuccessHandler((request, response, authentication) -> {});
        return authenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationFilter platformActorAuthenticationFilter,
            PlatformSecurityExceptionHandler platformSecurityExceptionHandler) {

        return http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/events/**", "/api/v1/assignments/**")
                        .permitAll()
                        .requestMatchers("/api/v1/**", "/reports/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(platformSecurityExceptionHandler)
                        .accessDeniedHandler(platformSecurityExceptionHandler))
                .addFilterBefore(platformActorAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
