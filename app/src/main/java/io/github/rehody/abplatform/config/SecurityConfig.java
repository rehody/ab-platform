package io.github.rehody.abplatform.config;

import io.github.rehody.abplatform.security.RequiresPlatformPermission;
import io.github.rehody.abplatform.web.security.PlatformActorIdJwtValidator;
import io.github.rehody.abplatform.web.security.PlatformJwtGrantedAuthoritiesConverter;
import io.github.rehody.abplatform.web.security.PlatformPermissionAuthorizationManager;
import io.github.rehody.abplatform.web.security.PlatformSecurityExceptionHandler;
import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.Pointcuts;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity(prePostEnabled = false)
public class SecurityConfig {

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
    public JwtDecoder jwtDecoder(
            PlatformAuthenticationProperties properties, PlatformActorIdJwtValidator platformActorIdJwtValidator) {

        SecretKeySpec secretKeySpec =
                new SecretKeySpec(properties.getSharedSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        NimbusJwtDecoder jwtDecoder =
                NimbusJwtDecoder.withSecretKey(secretKeySpec).build();

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getIssuer()), platformActorIdJwtValidator);

        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            PlatformJwtGrantedAuthoritiesConverter platformJwtGrantedAuthoritiesConverter) {

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setPrincipalClaimName("actorId");
        converter.setJwtGrantedAuthoritiesConverter(platformJwtGrantedAuthoritiesConverter);

        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            PlatformSecurityExceptionHandler platformSecurityExceptionHandler) {

        return http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers("/events/**", "/api/v1/assignments/**")
                        .permitAll()
                        .requestMatchers("/api/v1/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(platformSecurityExceptionHandler)
                        .accessDeniedHandler(platformSecurityExceptionHandler))
                .build();
    }
}
