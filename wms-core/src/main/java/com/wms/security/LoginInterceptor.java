package com.wms.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

import com.wms.model.entity.UserRole;
import com.wms.model.entity.UserEntity;
import com.wms.model.repository.UserRepository;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// security필터 대신 쓰는 문지기
// 컨트롤러 실행 전 쿠키의 토큰을 검사, true = 통과 / 예외 던지면 공통 예외처리로 401,403으로 응답
@Component
public class LoginInterceptor implements HandlerInterceptor {
    public static final String LOGIN_USER = "loginUser"; // request에 담아둘 이름

    @Autowired
    JwtProvider jwtProvider;
    @Autowired
    UserRepository userRepository;
    @Value("${auth.enabled:true}")
    private boolean enabled; // k6 성능 측정때만 false

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled)
            return true;

        // 1. 쿠키에서 토큰 꺼내기
        Cookie cookie = WebUtils.getCookie(request, "accessToken");
        if (cookie == null || cookie.getValue().isBlank())
            throw new UnauthorizedException("로그인이 필요합니다.");

        // 2. 서명-만료 검사 -> 누가 보냈는지 알아냄 // 팀장 변경
        Integer userId;
        try {
            userId = jwtProvider.parseUserId(cookie.getValue());
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("로그인이 만료되었습니다. 다시 로그인 해주세요");
        }
        // 2-1. DB 에서 최신 사용자 정보 (삭제된 계정이면 401) // 팀장 변경
        UserEntity entity = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("계정을 찾을 수 없습니다. 다시 로그인 해주세요"));
        LoginUser user = new LoginUser(entity.getUserId(), entity.getLoginId(),
                entity.getUserName(), entity.getRole());

        // 3. 관리자 전용 주소(작업자 계정, 기존정보)는 admin만
        if (user.role() != UserRole.ADMIN && isAdminOnly(request.getRequestURI()))
            throw new ForbiddenException("관리자만 사용할 수 있습니다.");

        // 4. 컨트롤러에서 꺼내 쓰도록 request에 보관
        request.setAttribute(LOGIN_USER, user);
        return true;
    }

    // 관리자 전용 주소: 작업자 계정 + 기존정보(품목,거래처,로케이션)
    private boolean isAdminOnly(String uri) {
        return uri.startsWith("/wms/workers")
                || uri.startsWith("/wms/product")
                || uri.startsWith("/wms/partner")
                || uri.startsWith("/wms/location");
    }
}
