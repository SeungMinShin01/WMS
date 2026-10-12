package com.wms.security;

import com.wms.model.entity.UserRole;

// 토큰에서 꺼낸 "지금 요청한 사람"
// 토큰 안에 든 정보
public record LoginUser(Integer userId, String loginId, String userName, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
