package com.wms.model.entity;

// 문서가 들어온 경로
public enum DocumentSource {
    WMS, // WMS 화면에서 직접 등록
    PORTAL // 화주 Portal 에서 MQ 로 들어옴
}