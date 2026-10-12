package com.wms.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class AuditLogReaderTest {

    @Test
    void 일반_줄은_13칸() {
        List<String> c = AuditLogReader.parseLine(
                "2026-10-12 10:00:00.000,1,admin,관리자,ADMIN,SHIP,documentId=9,,SUCCESS,,127.0.0.1,PUT /wms/outbounds/9/ship,12");
        assertThat(c).hasSize(13);
        assertThat(c.get(5)).isEqualTo("SHIP");
        assertThat(c.get(7)).isEmpty();
    }

    @Test
    void 따옴표_안_쉼표는_한칸() {
        List<String> c = AuditLogReader.parseLine(
                "t,,,,,INBOUND_UPLOAD,,\"originalFilename=a,b.xlsx size=10\",FAIL,\"3행: 품목 '없음'을(를) 찾을 수 없습니다.\",ip,uri,5");
        assertThat(c).hasSize(13);
        assertThat(c.get(7)).isEqualTo("originalFilename=a,b.xlsx size=10");
    }

    @Test
    void 따옴표_두개는_따옴표_하나() {
        List<String> c = AuditLogReader.parseLine("\"say \"\"hi\"\"\",b");
        assertThat(c).containsExactly("say \"hi\"", "b");
    }

    @Test
    void 쓰기와_읽기가_같은_값() {
        String original = "detail=1,2 \"x\"";
        List<String> c = AuditLogReader.parseLine(AuditWriter.csv(original) + ",end");
        assertThat(c.get(0)).isEqualTo(original);
    }
}