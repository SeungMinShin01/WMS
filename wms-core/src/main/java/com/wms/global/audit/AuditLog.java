package com.wms.global.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * @Target(METHOD) : 메서드에만 붙일 수 있음 클래스나 필드에 잘못 붙여도 컴파일이 통과됨
 * 
 * @Retention(RUNTIME) : 서버가 실행 중일 때도 어노테이션 정보가 남음 기본값(CLASS)은 실행 시점에 사라짐
 * → AOP가 @AuditLog를 못 찾아서 아무것도 기록 안 됨 (오류도 안 남)
 * 
 * @Documented : 문서(Javadoc)에 표시 동작에는 영향 없음
 * 
 * @interface : "새 어노테이션을 만든다"는 선언 , 자바 내부에서 어노테이션이 실제로 특별한 인터페이스
 * (java.lang.annotation.Annotation을 상속한 인터페이스)
 * 쓰는법 : @Foo 를 메서드·클래스 위에 붙임
 * 활용처 : @Override, @Transactional, @GetMapping
 */

@Target(ElementType.METHOD) // 메서드에만 붙일 수 있다.
@Retention(RetentionPolicy.RUNTIME) // 실행 중에도 남아 있어야 AOP가 읽을 수 있다.
@Documented // javadoc에 이 어노테이션이 붙어 있다고 표시
public @interface AuditLog {

    // 무슨 작업인가
    AuditAction action();

    // 대상 번호의 이름 예) "detailId" → target 칸에 "detailId:14"
    String target() default "";

    // detail 칸에 함께 남길 값 이름들 예) {"locationId"} -> "적재 location=13"

}
