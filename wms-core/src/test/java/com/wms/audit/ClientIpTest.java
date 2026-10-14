package com.wms.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

    @Test
    void X_Forwarded_For_위조는_무시하고_X_Real_IP를_쓴다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.1.20"); // Nginx 서버 사설 IP
        request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.7"); // 사용자가 넣은 가짜 + Nginx가 붙인 진짜
        request.addHeader("X-Real-IP", "203.0.113.7"); // Nginx가 덮어쓴 진짜

        assertThat(ClientIp.from(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void X_Real_IP가_없으면_접속_주소() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.0.5");
        request.addHeader("X-Forwarded-For", "6.6.6.6");

        assertThat(ClientIp.from(request)).isEqualTo("192.168.0.5");
    }

    @Test
    void X_Real_IP가_빈값이면_접속_주소() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.0.5");
        request.addHeader("X-Real-IP", "  ");

        assertThat(ClientIp.from(request)).isEqualTo("192.168.0.5");
    }

    @Test
    void IPv6_루프백은_127_0_0_1로() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("0:0:0:0:0:0:0:1");

        assertThat(ClientIp.from(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void 요청이_없으면_빈_문자열() {
        assertThat(ClientIp.from(null)).isEqualTo("");
    }
}