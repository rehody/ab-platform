package io.github.rehody.abplatform.web.security;

import io.github.rehody.abplatform.dto.response.ErrorResponse;
import io.github.rehody.abplatform.dto.response.ErrorResponse.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PlatformSecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication required";
    private static final String INVALID_AUTHENTICATION_TOKEN_MESSAGE = "Invalid authentication token";
    private static final String ACCESS_DENIED_MESSAGE = "Access denied";

    private final BearerTokenAuthenticationEntryPoint authenticationEntryPoint =
            new BearerTokenAuthenticationEntryPoint();

    private final BearerTokenAccessDeniedHandler accessDeniedHandler = new BearerTokenAccessDeniedHandler();

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException authException)
            throws IOException {
        authenticationEntryPoint.commence(request, response, authException);
        writeError(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED,
                resolveAuthenticationMessage(request));
    }

    @Override
    public void handle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AccessDeniedException accessDeniedException)
            throws IOException {
        accessDeniedHandler.handle(request, response, accessDeniedException);
        writeError(request, response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, ACCESS_DENIED_MESSAGE);
    }

    private String resolveAuthenticationMessage(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return AUTHENTICATION_REQUIRED_MESSAGE;
        }
        return INVALID_AUTHENTICATION_TOKEN_MESSAGE;
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            ErrorCode errorCode,
            String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse =
                ErrorResponse.of(status.value(), errorCode, message, request.getRequestURI(), List.of());

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
