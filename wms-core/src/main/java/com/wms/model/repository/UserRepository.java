package com.wms.model.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wms.model.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Integer>{
    Optional<UserEntity> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);    // 아이디 중복 검사
}
