package com.wms.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
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

// ED-60 입고 문서 등록 (sample2: 화주1 HLT, 공급사1 한빛식품 PT-001, 납품처4, 품목3 초코파이)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("classpath:db/sample/sample2.sql")
public class InboundRegisterApiTest {

    @Autowired
    MockMvc mockMvc;

    // 헤더 등록 후 documentId 반환
    private String createDocument(int partnerId) throws Exception {
        return mockMvc.perform(post("/wms/inbounds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":1,\"partnerId\":" + partnerId + ",\"expectedDate\":\"2026-10-20\"}"))
                .andReturn().getResponse().getContentAsString();
    }

    // 품목 1줄 등록 요청 본문
    private String item(String mfg, String expiry, int qty) {
        return "{\"productId\":3,\"manufactureDate\":\"" + mfg + "\",\"expiryDate\":\"" + expiry
                + "\",\"expectedQty\":" + qty + "}";
    }

    @Nested
    class 성공 {
        @Test
        void 헤더_품목_등록하면_문서번호와_LOT_자동생성() throws Exception {
            String id = createDocument(1);

            mockMvc.perform(post("/wms/inbounds/" + id + "/items")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(item("2026-10-01", "2027-04-01", 50)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/wms/inbounds/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documentNo", startsWith("HLT-IN-")))
                    .andExpect(jsonPath("$.status").value("WAITING"))
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].lotCode").value("LOT-20261001-PT-001-01"));
        }

        @Test
        void 공급사_선택지는_SUPPLIER만() throws Exception {
            mockMvc.perform(get("/wms/inbounds/tenants/1/suppliers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)));
        }
    }

    @Nested
    class 실패 {
        @Test
        void 납품처로_입고문서_400() throws Exception {
            mockMvc.perform(post("/wms/inbounds")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tenantId\":1,\"partnerId\":4,\"expectedDate\":\"2026-10-20\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 제조일자가_소비기한보다_늦으면_400() throws Exception {
            String id = createDocument(1);
            mockMvc.perform(post("/wms/inbounds/" + id + "/items")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(item("2027-05-01", "2027-04-01", 10)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 같은문서_같은LOT_두번_409() throws Exception {
            String id = createDocument(1);
            String body = item("2026-10-01", "2027-04-01", 10);
            mockMvc.perform(post("/wms/inbounds/" + id + "/items")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/wms/inbounds/" + id + "/items")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isConflict());
        }

        @Test
        void 같은LOT인데_소비기한_다르면_409() throws Exception {
            String first = createDocument(1);
            mockMvc.perform(post("/wms/inbounds/" + first + "/items")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(item("2026-10-01", "2027-04-01", 10)))
                    .andExpect(status().isCreated());

            String second = createDocument(1);
            mockMvc.perform(post("/wms/inbounds/" + second + "/items")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(item("2026-10-01", "2027-05-01", 10)))
                    .andExpect(status().isConflict());
        }
    }
}