package com.wms.service;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.wms.model.dto.inbound.CarryingDto;
import com.wms.model.dto.inbound.InspectionDto;

// ED-65 적재를 carryExecutor 대기열에 넣어 스레드가 처리 
// 별도 클래스인 이유: @Async는 프록시로 동작해 같은 클래스 안에서 부르면 비동기가 안걸림
@Service 
public class CarryAsyncService {
    @Autowired private InboundService inboundService;

    @Async ("carryExecutor")
    public CompletableFuture<Boolean> carry(CarryingDto dto){
        return CompletableFuture.completedFuture(inboundService.carry(dto));
    }

    // 입고 취소도 적재와 같은 대기열 — "적재 0개 확인 → 취소" 사이에 적재가 끼어들지 못하게
    @Async("carryExecutor")
    public CompletableFuture<String> cancel(Integer documentId){
        return CompletableFuture.completedFuture(inboundService.cancel(documentId));
    }

    // 검수도 같은 대기열 - "검수 안 된 품목 수 세기 -> 0이면 문서 검수완료"는 여러 행을 보는 규칙
    // 동시에 마지막 품목들을 검수하면 서로의 저장을 못봐서 아무도 상태를 못바꿈(write skew)
    // 한 줄로 세우면 앞 검수가 커밋된 뒤 다음 검수가 시작되므로 항상 최신 기록을 보고 판단
    @Async("carryExecutor")
    public CompletableFuture<Integer> inspect(InspectionDto dto){
        return CompletableFuture.completedFuture(inboundService.inspectionSave(dto));
    } 

}
