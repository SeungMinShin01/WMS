package com.wms.service;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import com.wms.security.ForbiddenException;

// 시연 데이터 초기화: Flyway 시드(db/seed/R__seed_demo.sql)를 그대로 다시 실행
// 업무 테이블을 비우고 시드 상태로 되돌림. flyway_schema_history 는 건드리지 않음
// demo.reset-enabled=true 인 서버(배포 시연 서버)에서만 동작 → 로컬·테스트에서 실수로 지우지 않게
@Service
public class DemoResetService {
    @Autowired
    private DataSource dataSource;

    @Value("${demo.reset-enabled:false}")
    private boolean enabled;

    public String reset() {
        if (!enabled) {
            throw new ForbiddenException("이 서버에서는 시연 데이터 초기화가 꺼져 있습니다.");
        }
        ClassPathResource seed = new ClassPathResource("db/seed/R__seed_demo.sql");
        try (Connection con = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(con, new EncodedResource(seed, StandardCharsets.UTF_8));
        } catch (SQLException e) {
            throw new IllegalStateException("초기화하지 못했습니다: " + e.getMessage());
        }
        return "초기화했습니다.";
    }
}