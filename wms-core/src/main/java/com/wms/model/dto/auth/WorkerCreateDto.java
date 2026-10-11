package com.wms.model.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 관리자가 작업자 계정을 만들 때 보내는 값
@NoArgsConstructor
@AllArgsConstructor
@Data
public class WorkerCreateDto {
    private String loginId;
    private String password;
    private String userName;
}