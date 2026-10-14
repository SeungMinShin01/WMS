package com.wms.service;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.wms.model.dto.inbound.CarryingDto;

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

}
