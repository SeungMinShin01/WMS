package com.wms.controller.auth;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.auth.LoginRequestDto;
import com.wms.model.dto.auth.LoginResponseDto;
import com.wms.security.LoginInterceptor;
import com.wms.security.LoginUser;
import com.wms.service.AuthService;

@RequestMapping("/auth")
@RestController 
public class AuthController {
    @Autowired private AuthService authService;

    // 로그인 -> 토큰을 httpOnly 쿠키로 심어줌(body앤 사용자 정보만)
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto dto){
        LoginResponseDto result = authService.login(dto);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, tokenCookie(result.getToken(), Duration.ofHours(1)).toString()).body(result);
    }

    // 로그아웃 -> 같은 이름의 쿠키를 수명을 0으로 덮어써 지움
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(){
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, tokenCookie("", Duration.ZERO).toString())
        .build();
    }

    // 쿠키 확인용: 인터셉터가 토큰을 검사해 request에 넣어둔 "나"를 돌려줌
    @GetMapping("/me")
    public ResponseEntity<LoginUser> me(
            @RequestAttribute(name = LoginInterceptor.LOGIN_USER, required = false) LoginUser me){
        if(me == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(me);
    }

    // 토큰 쿠키 만들기
    private ResponseCookie tokenCookie(String value, Duration maxAge){
        return ResponseCookie.from("accessToken", value)
                .httpOnly(true)         // JS가 못 읽음 -> 토큰 탈취 방지
                .secure(false)            // 로컬 http 개발용 -> 배포후 true
                .sameSite("Lax")        // 다른 사이트에서 보낸 post/put에는 쿠키가 안 붙음
                .path("/")                  // 모든 경로 요청에 첨부
                .maxAge(maxAge)                   // 1시간
                .build();
    }
}
