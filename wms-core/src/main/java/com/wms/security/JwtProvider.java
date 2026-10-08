package com.wms.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.wms.model.entity.UserEntity;
import com.wms.model.entity.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// 토큰 만들기-읽기
@Component 
public class JwtProvider {
    private final SecretKey key;        // 서명 키 (서버만 아는 정보)
    private final long expirationMs;    // 유효 시간

    // @Value: application.yml 의 jwt.* 값을 받아옴
    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration-ms}") long expirationMs){
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;                
    }

    // 로그인 성공 시 토큰 발급: sub = userId, role
    public String createToken(UserEntity user){
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    // 토큰 검증 + 내용 꺼내기. 위조 만료면 JwtException
    public LoginUser parse(String token){
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return new LoginUser(
            Integer.valueOf(claims.getSubject()),
            UserRole.valueOf(claims.get("role", String.class)));
    }
}