package com.wms.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public final class LoginUser {

    // requset attribute(보관함)에 넣고 꺼낼때 쓰는 태그(이름표)
    public static final String ATTRIBUTE = "loginUser";

    // 인증 정보가 없을 때 null 대신 쓰는 값

    public static final LoginUser UNKNOWN = new LoginUser(null, "unknown", "unknown", "unknown");

    private final Integer userId; // 회원번호 (토큰의 sub)
    private final String loginId; // 로그인 아이디
    private final String name; // 이름
    private final String role; // ADMIN / WORKER

    // 감사 로그용 : 없으면 UNKNOWN (기록 때문에 요청이 실패하면 안됨. 예외를 내지 않음)
    public static LoginUser from(HttpServletRequest requset) {
        if (requset == null) {
            return UNKNOWN;
        }

        Object value = requset.getAttribute(ATTRIBUTE);
        if (value instanceof LoginUser loginUser) {
            return loginUser;
        }
        return UNKNOWN;
    }

    // 업무용 : 반드시 로그인 사용자가 있어야함 (로그인을 안하면 접근 제한걸 예정이라)
    public static LoginUser require(HttpServletRequest requset) {
        LoginUser loginUser = from(requset);
        if (loginUser == UNKNOWN) {
            throw new IllegalStateException("로그인 정보가 없습니다.");
        }
        return loginUser;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}