package com.wms.security;

// 403: 로그인은 했지만 권한이 없음 (작업자가 관리자 기능 호출)
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}