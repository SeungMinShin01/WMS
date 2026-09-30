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
public class OutboundApiTest {

    @Autowired
    MockMvc mockMvc;

    @Nested
    class 성공 {

        @Test
        void 출고목록_8건_예정일순() throws Exception {
            mockMvc.perform(get("/wms/outbounds"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(8)))
                    .andExpect(jsonPath("$[0].documentNo").value("OUT-20260926-001"));
        }

        @Test
        void 출고상세_품목2개() throws Exception {
            mockMvc.perform(get("/wms/outbounds/9"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ALLOCATED"))
                    .andExpect(jsonPath("$.items", hasSize(2)));
        }

        @Test
        void 피킹리스트_로케이션순() throws Exception {
            mockMvc.perform(get("/wms/allocations/9"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].locationCode").value("A-01-03"))
                    .andExpect(jsonPath("$[1].locationCode").value("A-01-04"));
        }
    }

}