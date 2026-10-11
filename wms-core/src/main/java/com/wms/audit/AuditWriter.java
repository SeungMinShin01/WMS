package com.wms.audit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.wms.security.LoginUser;

/*
 * 감사 로그 CSV 한 줄을 만들어 "AUDIT" 로거로 보낸다
 * 
 * - 실제 파일 쓰기는 logback-spring.xml로
 * → 이 클래스는 "한 줄을 만드는 것" 까지만 책임진다.
 */

@Component
public class AuditWriter {

    // 이름이 "AUDIT" 인 로거. logback 설정에서 이 이름으로 받아 audit.csv로 보낸다
    // LoggerFactory : 로거(Logger) 객체를 만들어 주는 공장, . SLF4J라는 로그 라이브러리에 들어 있다.
    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT"); // 이름이 "AUDIT" 인 로거를 달라

    // CSV 맨 윗줄(열) 이름. logback 설정의 fileHeader 와 같은 내용
    // logback : 로그를 실제로 어디에, 어떤 모양으로 쓸지 처리하는 라이브러리. Spring Boot에 기본내장
    public static final String HEADER = "time,userId,loginId,userName,role,action,target,detail,result,reason,ip,uri,ms";

    // DateTime 포매터
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public void write(LocalDateTime time, LoginUser user, AuditAction action, String target, String detail,
            boolean success, String reason, String ip, String uri, long elapseMs) {
        String line = String.join(",",
                csv(time.format(TIME_FORMAT)),
                csv(user == null || user.userId() == null ? "" : String.valueOf(user.userId())),
                csv(user == null ? "" : user.loginId()),
                csv(user == null ? "" : user.userName()),
                csv(user == null || user.role() == null ? "" : user.role().name()),
                csv(action.name()),
                csv(target),
                csv(detail),
                csv(success ? "SUCCESS" : "FAIL"),
                csv(reason),
                csv(ip),
                csv(uri),
                csv(String.valueOf(elapseMs)));
        AUDIT.info(line); // AUDIT 로거로 로그 남기기

    }

    /*
     * CSV 칸 하나 만들기 (RFC 4180)
     * 줄바꿈 → 공백 : 한 줄 = 한 건이 깨지지 않게
     * = + - @ 로 시작하면 앞에 ' : 엑셀이 수식으로 실행하는 것(CSV 인젝션) 방지
     * 쉼표·따옴표가 있으면 "..." 로 감싸고, 안의 " 는 "" 로
     */

    static String csv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = value.replace("\r", " ").replace("\n", " ");
        // 첫 글자가 =, +, -, @ 중 하나인가 -> 왜? CSV 인젝션, 엑셀은 칸이 =, +, -, @로 시작하면 수식으로 실행
        if ("=+-@".indexOf(text.charAt(0)) >= 0) {
            // 왜 앞에 '를 붙이는가? -> 작은따옴표(')로 시작하는 칸을 엑셀은 글자로 처리하고 수식으로 실행하지 않음
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}