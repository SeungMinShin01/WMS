package com.wms.service;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.wms.model.dto.inbound.CarryingDto;

// ED-65 적재를 carryExecutor 대기열에 넣어 스레드가 처리 
// 별도 클래스인 이유: @Async는 프록시로 동작해 같은 클래스 안에서 부르면 비동기가 안걸림
@Service 
public class CarryAsyncService {
    @Autowired private InboundService inboundService;
    @Value("${carry.sync}") private boolean sync;   // true면 동기화로 한명씩

    private final Object lock = new Object();   // 동기화에 쓸 자물쇠

    @Async ("carryExecutor")
    public CompletableFuture<Boolean> carry(CarryingDto dto){
        if(sync){
            // 이 메소드는 트랜젝션 밖이여서 락 안에서 inboundservice.carry의 커밋까지 끝남
            synchronized(lock){
                return CompletableFuture.completedFuture(inboundService.carry(dto));
            }
        }
        return CompletableFuture.completedFuture(inboundService.carry(dto));
    }
}
