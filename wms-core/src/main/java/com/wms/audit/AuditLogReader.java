package com.wms.audit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// audit.csv 읽기 (쓰기는 AuditWriter + logback)
// 오늘은 audit.csv, 지난 날은 audit.2026-10-11.csv (logback 이 날짜가 바뀐 뒤 첫 기록 때 이름을 바꿈)
@Component
public class AuditLogReader {

    @Value("${logging.file.path:logs}") // logback-spring.xml 의 LOG_DIR 과 같은 값
    private String logDir;

    Path fileOf(LocalDate date) {
        String name = date.equals(LocalDate.now()) ? "audit.csv" : "audit." + date + ".csv";
        return Path.of(logDir, name);
    }

    // 하루치를 최신순으로
    public List<AuditLogDto> read(LocalDate date) {
        List<AuditLogDto> rows = new ArrayList<>();
        for (String line : lines(date)) {
            List<String> c = parseLine(line);
            if (c.size() < 13)
                continue; // 깨진 줄은 건너뜀
            rows.add(new AuditLogDto(c.get(0), c.get(1), c.get(2), c.get(3), c.get(4),
                    c.get(5), label(c.get(5)), c.get(6), c.get(7),
                    c.get(8), c.get(9), c.get(10), c.get(11), c.get(12)));
        }
        Collections.reverse(rows);
        return rows;
    }

    // 다운로드용 파일: BOM(엑셀 한글) + 제목 줄 + 원본 줄
    public byte[] download(LocalDate date) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });
        out.writeBytes((AuditWriter.HEADER + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (String line : lines(date)) {
            out.writeBytes((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private List<String> lines(LocalDate date) {
        Path file = fileOf(date);
        if (!Files.exists(file))
            return List.of(); // 그날 기록 없음
        try {
            List<String> result = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("time,"))
                    continue;
                result.add(line);
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("감사 로그를 읽을 수 없습니다.");
        }
    }

    // ACTION 영문 → 한글 (없는 값이면 영문 그대로)
    private String label(String action) {
        try {
            return AuditAction.valueOf(action).getLabel();
        } catch (IllegalArgumentException e) {
            return action;
        }
    }

    // CSV 한 줄 → 칸 목록. AuditWriter.csv 의 반대 규칙
    // "..." 안의 쉼표는 칸 구분이 아님, "" 는 " 하나
    static List<String> parseLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                cells.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        cells.add(cur.toString());
        return cells;
    }
}