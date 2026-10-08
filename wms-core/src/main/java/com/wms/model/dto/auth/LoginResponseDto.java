package com.wms.model.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor 
@AllArgsConstructor 
@Data 
@Builder 
// @JsonIgnore = 컨트롤러가 쿠키를 만들 때만 쓰고, 화면으로 가는 JSON에는 빠짐
public class LoginResponseDto {
    @JsonIgnore     // 토큰은 응답 body에 넣지 않고 쿠키로만 보낸다
    private String token;
    private String loginId;
    private String userName;
    private String role;        // ADMIN / WORKER
}
