package com.wms.controller;

import java.util.concurrent.CompletionException;


import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.wms.security.ForbiddenException;
import com.wms.security.UnauthorizedException;

import jakarta.persistence.EntityNotFoundException;

// 서비스에서 던진 예외가 여기로 온다.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 400 Bad Request - 클라이언트 요청 오류 (수량 0 이하, 필수값 없음, 형식 위반)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    // 404 Not Found - 리소스 없음 (없는 문서 ID, 없는 재고 ID, 없는 로케이션)
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<String> NotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    // 409 Conflict - 요청이 현재 서버의 상태와 충돌 (상태 전이 위반, 중복 처리, 재고 부족)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    // 401 Unauthorized - 로그인 필요 / 토큰 만료·위조 / 아이디·비밀번호 불일치
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<String> unauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    // 403 Forbidden - 로그인은 했지만 권한 없음 (WORKER가 관리자 API 호출)
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<String> forbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
    // 비동기(@Async) 작업에서 난 예외는 CompletionException 포장지에 싸여서 온다 (ED-65)
    // → 안의 진짜 예외를 꺼내 위의 400/404/409 처리에 그대로 넘긴다
    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<String> completion(CompletionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof IllegalArgumentException c) return badRequest(c);
        if (cause instanceof EntityNotFoundException c) return NotFound(c);
        if (cause instanceof IllegalStateException c) return conflict(c);
        return serverError(e);
    }

    // 500 Internal Server Error - 서버 오류 (원인은 로그로만, 화면엔 고정 문장)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> serverError(Exception e){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류가 발생했습니다.");
    }

    // 503 Service Unavailable - 적재 대기열이 가득 차서 요청을 받을 수 없음 (ED-65)
    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<String> busy(TaskRejectedException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("요청이 많아 잠시 후 다시 시도하세요.");
    }

}
