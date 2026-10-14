package com.wms.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity 
@Table(name="user_account")
@NoArgsConstructor 
@AllArgsConstructor 
@Data 
@Builder 
public class UserEntity extends BaseTime{
        @Id 
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer userId;

        private String loginId;
        private String password;    // BCrypt 해시
        private String userName;

        @Enumerated(EnumType.STRING)
        private UserRole role;

        // 계정 사용 여부 (false = 비활성 → 로그인·요청 모두 401). 삭제하지 않고 막기만 함
        @Builder.Default
        private Boolean isActive = true;

}
