package com.wms.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("classpath:db/sample/sample2.sql")
public class OutboundRegisterApiTest {

    @Autowired
    MockMvc mockMvc;

    // 헤더 등록 후 documentId 반환
    private String createDocument(int partnerId) throws Exception {
        return mockMvc.perform(post("/wms/outbounds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":1,\"partnerId\":" + partnerId + ",\"expectedDate\":\"2026-10-20\"}"))
                .andReturn().getResponse().getContentAsString();
    }

    @Nested
    class 성공 {
        @Test
        void 헤더_품목_등록하면_상세에_품목1줄() throws Exception {
            String id = createDocument(4);

            mockMvc.perform(post("/wms/outbounds/" + id + "/items")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"productId\":1,\"expectedQty\":10}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/wms/outbounds/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("WAITING"))
                    .andExpect(jsonPath("$.items", hasSize(1)));
        }

        @Test
        void 납품처_선택지는_CUSTOMER만() throws Exception {
            mockMvc.perform(get("/wms/outbounds/tenants/1/customers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }
    }

    @Nested
    class 실패 {
        @Test
        void 공급사로_출고문서_400() throws Exception {
            mockMvc.perform(post("/wms/outbounds")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tenantId\":1,\"partnerId\":1,\"expectedDate\":\"2026-10-20\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 같은품목_두번_409() throws Exception {
            String id = createDocument(4);
            String body = "{\"productId\":1,\"expectedQty\":10}";
            mockMvc.perform(post("/wms/outbounds/" + id + "/items")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/wms/outbounds/" + id + "/items")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isConflict());
        }
    }
}