package com.wms.security;

// 401: 로그인 안 했거나, 토큰이 위조/만료됐거나, 아이디·비번 불일치
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) { super(message); }
}