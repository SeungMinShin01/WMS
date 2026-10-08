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

}
