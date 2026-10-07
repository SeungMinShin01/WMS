package com.wms.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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


    


    // 500 Internal Server Error - 서버 오류 (원인은 로그로만, 화면엔 고정 문장)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> serverError(Exception e){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류가 발생했습니다.");
    }

    // 500 코드를 세분화 (등록)
    @ExceptionHandler(SaveException.class)
    public ResponseEntity<String> saveError(SaveException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body( e.getMessage());
    }
}
