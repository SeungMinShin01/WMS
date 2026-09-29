package com.wms.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "location")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class LocationEntity extends BaseTime {
    // 유린님
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer locationId;

    private String locationCode;

    @Builder.Default
    private Boolean isActive = true;
    
    private Integer capacity; // 칸당 최대 적재량 (단위: BOX), null = 제한 없음 (V2)
}