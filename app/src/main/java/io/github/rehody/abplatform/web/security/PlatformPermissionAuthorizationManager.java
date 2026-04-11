package io.github.rehody.abplatform.web.security;

import io.github.rehody.abplatform.security.PlatformPermission;
import io.github.rehody.abplatform.security.RequiresPlatformPermission;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class PlatformPermissionAuthorizationManager implements AuthorizationManager<MethodInvocation> {

    @Override
    public AuthorizationDecision authorize(
            Supplier<? extends Authentication> authentication, MethodInvocation methodInvocation) {
        PlatformPermission requiredPermission = getRequiredPermission(methodInvocation);
        Authentication currentAuthentication = authentication.get();
        if (currentAuthentication == null || !currentAuthentication.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        boolean granted = currentAuthentication.getAuthorities().stream()
                .anyMatch(authority -> requiredPermission.name().equals(authority.getAuthority()));

        return new AuthorizationDecision(granted);
    }

    private PlatformPermission getRequiredPermission(MethodInvocation methodInvocation) {
        RequiresPlatformPermission methodPermission =
                AnnotationUtils.findAnnotation(methodInvocation.getMethod(), RequiresPlatformPermission.class);
        if (methodPermission != null) {
            return methodPermission.value();
        }

        Class<?> targetClass = getTargetClass(methodInvocation);
        RequiresPlatformPermission classPermission =
                AnnotationUtils.findAnnotation(targetClass, RequiresPlatformPermission.class);

        if (classPermission != null) {
            return classPermission.value();
        }

        throw new IllegalStateException(
                "Missing @RequiresPlatformPermission for method '%s'".formatted(methodInvocation.getMethod()));
    }

    private Class<?> getTargetClass(MethodInvocation methodInvocation) {
        Object target = methodInvocation.getThis();
        if (target == null) {
            return methodInvocation.getMethod().getDeclaringClass();
        }
        return AopUtils.getTargetClass(target);
    }
}
