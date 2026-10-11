package com.wms.audit;

// 감사 로그 한 줄 (audit.csv 13칸 + 작업 한글 이름)
public record AuditLogDto(
        String time, String userId, String loginId, String userName, String role,
        String action, String actionLabel, String target, String detail,
        String result, String reason, String ip, String uri, String ms) {
}