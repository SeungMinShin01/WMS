package com.wms.security;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

// @Order(2): 감사 로그(@Order(1)) 안쪽에서 실행 → 403 도 감사 로그에 FAIL 로 남는다

@Aspect
@Component
@Order(2)
public class AuthorizationAspect {

    @Value("${auth.enabled:true}")
    private boolean enabled; // k6 성능 측정 때는 인터셉터와 같이 꺼짐

    // @annotation: 메서드에 붙은경우 / @within: 클래스에 붙은 경우
    @Before("@annotation(com.wms.security.AdminOnly) || @within(com.wms.security.AdminOnly)")
    public void checkAdmin() {
        if (!enabled)
            return;

        LoginUser user = currentUser();
        if (user == null || !user.isAdmin())
            throw new ForbiddenException("관리자만 사용할 수 있습니다.");
    }

    private LoginUser currentUser() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute(LoginInterceptor.LOGIN_USER) instanceof LoginUser user) {
            return user;
        }
        return null;
    }
}