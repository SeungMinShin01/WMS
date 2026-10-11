package com.wms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// ED-65 적재 전용 스레드풀: 크기는 application.yml / 실행 인자로 바꿔 끼운다
@Configuration 
@EnableAsync 
public class AsyncConfig {
    @Bean (name="carryExecutor")
    public ThreadPoolTaskExecutor carryExecutor(
            @Value ("${carry.pool.core}") int core,
            @Value("${carry.pool.max}") int max,
            @Value("${carry.pool.queue}") int queue){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core); // 기본 워커 수
        executor.setMaxPoolSize(max);   // 대기열이 꽉 찼을 때만 여기까지 늘어남
        executor.setQueueCapacity(queue);   // 대기열 길이 (넘치면 거부)
        executor.setThreadNamePrefix("carry-"); // 로그에 carry-1 ~~로 찍힘
        return executor;    // initialize()는 Spring이 bean 생성 때 호출
        }
}

/*
스레드풀 동작 순서:

워커가 core 수보다 적으면 새 워커를 만듭니다.
core가 다 차면 대기열에 넣습니다.
대기열까지 꽉 차야 max까지 워커를 늘립니다.
그래도 넘치면 **거부(TaskRejectedException)**합니다.
*/