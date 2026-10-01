package com.wms.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("classpath:db/sample/sample2.sql")
public class InboundApiTest {

    @Autowired
    MockMvc mockMvc;

    @Nested
    class 성공 {

        @Test
        void 입고목록_8건_예정일순() throws Exception {
            mockMvc.perform(get("/wms/inbounds"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(8)))
                    .andExpect(jsonPath("$[0].documentNo").value("IN-20260922-001"))
                    .andExpect(jsonPath("$[0].status").value("COMPLETED"));
        }

        @Test
        void 입고상세_품목3개() throws Exception {
            mockMvc.perform(get("/wms/inbounds/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documentNo").value("IN-20260922-001"))
                    .andExpect(jsonPath("$.items", hasSize(3)));
        }
    }

    @Nested
    class 실패 {

        @Test
        void 입고상세_없는문서_404() throws Exception {
            mockMvc.perform(get("/wms/inbounds/9999"))
                    .andExpect(status().isNotFound());
        }
    }
}