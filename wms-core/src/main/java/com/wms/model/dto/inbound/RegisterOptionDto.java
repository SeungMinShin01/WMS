package com.wms.model.dto.inbound;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor @AllArgsConstructor @Data 
public class RegisterOptionDto {
    private Integer id;
    private String code;
    private String name;
}
