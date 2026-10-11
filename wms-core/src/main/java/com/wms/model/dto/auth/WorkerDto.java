package com.wms.model.dto.auth;

import com.wms.model.entity.UserEntity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 계정 목록 응답 (비밀번호는 절대 내보내지 않음)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class WorkerDto {
    private Integer userId;
    private String loginId;
    private String userName;
    private String role;

    public static WorkerDto from(UserEntity u){
        return new WorkerDto(u.getUserId(), u.getLoginId(), u.getUserName(), u.getRole().name());
    }
}