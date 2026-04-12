package io.github.rehody.abplatform.web.security;

import io.github.rehody.abplatform.security.PlatformPermission;
import io.github.rehody.abplatform.security.RequiresPlatformPermission;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.NonNull;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class PlatformPermissionAuthorizationManager implements AuthorizationManager<MethodInvocation> {

    @Override
    public AuthorizationDecision authorize(
            @NonNull Supplier<? extends Authentication> authentication, MethodInvocation methodInvocation) {

        PlatformPermission requiredPermission = getRequiredPermission(methodInvocation);
        AuthorizationResult result = AuthorityAuthorizationManager.<MethodInvocation>hasAuthority(
                        requiredPermission.name())
                .authorize(authentication, methodInvocation);

        return new AuthorizationDecision(result.isGranted());
    }

    private PlatformPermission getRequiredPermission(MethodInvocation methodInvocation) {
        Class<?> targetClass = getTargetClass(methodInvocation);
        Method method = AopUtils.getMostSpecificMethod(methodInvocation.getMethod(), targetClass);

        RequiresPlatformPermission methodPermission =
                AnnotationUtils.findAnnotation(method, RequiresPlatformPermission.class);

        if (methodPermission != null) {
            return methodPermission.value();
        }

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
