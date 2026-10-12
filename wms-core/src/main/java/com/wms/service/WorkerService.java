package com.wms.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.wms.model.dto.auth.WorkerCreateDto;
import com.wms.model.dto.auth.WorkerDto;
import com.wms.model.entity.UserEntity;
import com.wms.model.entity.UserRole;
import com.wms.model.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class WorkerService {
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // 계정 목록 (관리자 화면)
    public List<WorkerDto> findAll(){
        return userRepository.findAll().stream().map(WorkerDto::from).toList();
    }

    // 작업자 생성 -> userId 반환. 역할은 WORKER 고정 (API로 관리자를 만들 수 없게)
    @Transactional
    public Integer create(WorkerCreateDto dto){
        // 필수값·형식 400
        if(!StringUtils.hasText(dto.getLoginId()) || !StringUtils.hasText(dto.getPassword())
                || !StringUtils.hasText(dto.getUserName()))
            throw new IllegalArgumentException("아이디, 비밀번호, 이름은 필수입니다.");
        String loginId = dto.getLoginId().trim();
        if(loginId.length() < 4 || loginId.length() > 20)
            throw new IllegalArgumentException("아이디는 4~20자로 입력하세요.");
        if(dto.getPassword().length() < 4)
            throw new IllegalArgumentException("비밀번호는 4자 이상이어야 합니다.");

        // 아이디 중복 409 (1차: 미리 검사)
        if(userRepository.existsByLoginId(loginId))
            throw new IllegalStateException("이미 사용 중인 아이디입니다.");

        UserEntity user = UserEntity.builder()
                .loginId(loginId)
                .password(passwordEncoder.encode(dto.getPassword()))  // BCrypt 해시로 저장
                .userName(dto.getUserName().trim())
                .role(UserRole.WORKER)
                .build();
        try {
            // saveAndFlush: INSERT를 지금 바로 실행해서 UNIQUE 위반을 여기서 잡음
            return userRepository.saveAndFlush(user).getUserId();
        } catch (DataIntegrityViolationException e) {
            // 2차: 동시에 같은 아이디로 두 번 요청 → 둘 다 1차를 통과해도 DB UNIQUE가 막음
            throw new IllegalStateException("이미 사용 중인 아이디입니다.");
        }
    }
}