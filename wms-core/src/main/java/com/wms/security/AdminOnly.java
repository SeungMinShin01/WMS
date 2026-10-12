package com.wms.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 관리자 (ADMIN)만 실행할 수 있는 API 표시
// 메서드에 붙이면 그 메서드만, 클래스에 붙이면 그 컨트롤러 전체
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOnly {
}