package com.wms.audit;

import jakarta.servlet.http.HttpServletRequest;

final class ClientIp {

    private ClientIp() {
    }

    static String from(HttpServletRequest request) {
        if (request == null)
            return "";
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return normalize(realIp.trim());
        }
        return normalize(request.getRemoteAddr());
    }

    static String normalize(String ip) {
        if (ip == null)
            return "";
        if (ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1"))
            return "127.0.0.1";

        return ip;
    }
}
