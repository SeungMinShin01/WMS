package com.wms.model.dto.inbound;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarryResultDto {
    // ED-16 적재 결과
    private String result;    // OK: 적재됨 / WARN: 정책 경고 (확인 필요) / FAIL: 적재 불가
    private String message;   // 경고·실패 사유

    public static CarryResultDto ok()               { return new CarryResultDto("OK", null); }
    public static CarryResultDto warn(String msg)   { return new CarryResultDto("WARN", msg); }
}