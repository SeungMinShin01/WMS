package com.wms.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.wms.security.LoginInterceptor;

@Configuration 
public class WebConfig implements WebMvcConfigurer{
    @Autowired private LoginInterceptor loginInterceptor;

    // 토큰 검사할 주소: 업무 api 전체 + 내 정보
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/wms/**", "/auth/me");
    }

    // 비밀번호 암호화기 하나를 만들어 AuthService·WorkerService가 같이 씀
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
