package com.wms.model.entity;

import org.springframework.context.annotation.Primary;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 엔티티 DB 참고!!
@Entity
@Table(name = "location")
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 생성자
@Data // Getter, Setter, toString 종합 선물 세트
@Builder // 빌더 패턴을 쓸 수 있게 해준다. 깔끔 안전 코드 가능
public class LocationEntity extends BaseTime {
    // BaseTime = 날짜 관리 클래스
    // 데이터 언제 만들어졌고 언제 수정됐는지 알기 때문에 이거로 상속 받는 거임
    // 유린님
    @Id
    // 이 변수가 이 테이블의 주민등록번호(Primary Key, 고유 식별자)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 자동 번호 매김
    private Integer locationId;
    // 위치의 고유 번호
    private String locationCode;
    // 창고의 구역이나 위치 이름을 글자로 저장
    @Builder.Default
    private Boolean isActive = true;

    private Integer capacity; // 칸당 최대 적재량 (단위: BOX), null = 제한 없음 (V2)
}