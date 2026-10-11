package com.wms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.wms.model.dto.auth.LoginRequestDto;
import com.wms.model.dto.auth.LoginResponseDto;
import com.wms.model.entity.UserEntity;
import com.wms.model.repository.UserRepository;
import com.wms.security.JwtProvider;
import com.wms.security.UnauthorizedException;

@Service
@Transactional(readOnly = true)
public class AuthService {
    // 아이디가 없든 비번이 틀리든 같은 문구 -> 어떤 아이디가 있는지 알려주지 않음
    private static final String LOGIN_FAIL = "아이디 또는 비밀번호가 올바르지 않습니다.";

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    public LoginResponseDto login(LoginRequestDto dto){
        if(!StringUtils.hasText(dto.getLoginId()) || !StringUtils.hasText(dto.getPassword()))
            throw new IllegalArgumentException("아이디와 비밀번호를 입력하세요.");

        UserEntity user = userRepository.findByLoginId(dto.getLoginId().trim())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_FAIL));
        if(!passwordEncoder.matches(dto.getPassword(), user.getPassword()))
            throw new UnauthorizedException(LOGIN_FAIL);

        return LoginResponseDto.builder()
                .token(jwtProvider.createToken(user))
                .loginId(user.getLoginId())
                .userName(user.getUserName())
                .role(user.getRole().name())
                .build();
    }
}