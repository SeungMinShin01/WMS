package com.wms.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// 화주 1, 2 는 V4 시드(3곳 고정)에 있다고 가정
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("classpath:db/sample/sample2.sql")
public class ProductApiTest {

        @Autowired
        MockMvc mockMvc;

        // ---------- 도우미 ----------

        // null 인 값은 JSON 에서 아예 뺀다 (화주 없음 시험용)
        private String body(Integer productId, Integer tenantId, String code, String name,
                        String spec, String unit, Integer minShipDays) {
                List<String> parts = new ArrayList<>();
                if (productId != null)
                        parts.add("\"productId\":" + productId);
                if (tenantId != null)
                        parts.add("\"tenantId\":" + tenantId);
                if (code != null)
                        parts.add("\"productCode\":\"" + code + "\"");
                if (name != null)
                        parts.add("\"productName\":\"" + name + "\"");
                if (spec != null)
                        parts.add("\"spec\":\"" + spec + "\"");
                if (unit != null)
                        parts.add("\"unit\":\"" + unit + "\"");
                if (minShipDays != null)
                        parts.add("\"minShipDays\":" + minShipDays);
                return "{" + String.join(",", parts) + "}";
        }

        // 정상 등록 본문
        private String 등록본문(Integer tenantId, String code) {
                return body(null, tenantId, code, "테스트품목", "120g*40", "BOX", null);
        }

        // 등록하고 새 품목 번호를 돌려준다
        private int 등록(Integer tenantId, String code) throws Exception {
                MvcResult r = mockMvc.perform(post("/wms/product")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(등록본문(tenantId, code)))
                                .andExpect(status().isOk())
                                .andReturn();
                return Integer.parseInt(r.getResponse().getContentAsString().trim());
        }

        @Nested
        class 성공 {

                @Test
                void 등록_새번호반환() throws Exception {
                        mockMvc.perform(post("/wms/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(등록본문(1, "SKU-T001")))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$").isNumber());
                }

                // 다른 화주는 같은 코드 허용
                @Test
                void 다른화주_같은코드_허용() throws Exception {
                        등록(1, "SKU-T001");
                        mockMvc.perform(post("/wms/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(등록본문(2, "SKU-T001")))
                                        .andExpect(status().isOk());
                }

                @Test
                void 전체조회_화주없이() throws Exception {
                        mockMvc.perform(get("/wms/products"))
                                        .andExpect(status().isOk());
                }

                // 화주 2 로 거르면 화주 2 품목만 나오고, 방금 등록한 것이 보인다
                @Test
                void 전체조회_화주로_거르기() throws Exception {
                        등록(2, "SKU-T001");
                        mockMvc.perform(get("/wms/products").param("tenantId", "2"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$[*].tenantId", everyItem(equalTo(2))))
                                        .andExpect(jsonPath("$[?(@.productCode == 'SKU-T001')]", hasSize(1)));
                }

                @Test
                void 개별조회() throws Exception {
                        int id = 등록(1, "SKU-T001");
                        mockMvc.perform(get("/wms/product/detail").param("productid", String.valueOf(id)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.productCode").value("SKU-T001"))
                                        .andExpect(jsonPath("$.tenantId").value(1));
                }

                @Test
                void 출고허용잔여일_등록_조회() throws Exception {
                        MvcResult r = mockMvc.perform(post("/wms/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body(null, 1, "SKU-T001", "테스트품목", "120g*40", "BOX", 30)))
                                        .andExpect(status().isOk())
                                        .andReturn();
                        int id = Integer.parseInt(r.getResponse().getContentAsString().trim());

                        mockMvc.perform(get("/wms/product/detail").param("productid", String.valueOf(id)))
                                        .andExpect(jsonPath("$.minShipDays").value(30));
                }

                // 코드는 그대로 두고 이름만 바꿈 → 자기 자신은 중복이 아니다
                @Test
                void 수정_코드그대로_이름만() throws Exception {
                        int id = 등록(1, "SKU-T001");
                        mockMvc.perform(put("/wms/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body(id, 1, "SKU-T001", "이름수정", "120g*40", "BOX", null)))
                                        .andExpect(status().isOk())
                                        .andExpect(content().string("true"));

                        mockMvc.perform(get("/wms/product/detail").param("productid", String.valueOf(id)))
                                        .andExpect(jsonPath("$.productName").value("이름수정"));
                }

                @Test
                void 수정_출고허용잔여일_변경() throws Exception {
                        int id = 등록(1, "SKU-T001");
                        mockMvc.perform(put("/wms/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body(id, 1, "SKU-T001", "테스트품목", "120g*40", "BOX", 45)))
                                        .andExpect(status().isOk());

                        mockMvc.perform(get("/wms/product/detail").param("productid", String.valueOf(id)))
                                        .andExpect(jsonPath("$.minShipDays").value(45));
                }
        }

        @Nested
        class 실패 {

                @Test
                void 등록_화주없음_400() throws Exception {
                        mockMvc.perform(post("/wms/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(등록본문(null, "SKU-T001")))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                void 등록_없는화주_404() throws Exception {
                        mockMvc.perform(post("/wms/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(등록본문(99, "SKU-T001")))
                                        .andExpect(status().isNotFound());
                }

                @Test
                void 등록_품목명_비움_400() throws Exception {
                        mockMvc.perform(post("/wms/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body(null, 1, "SKU-T001", "", "120g*40", "BOX", null)))
                                        .andExpect(status().isBadRequest());
                }

                // 품목명, 규격을 둘 다 비우면 문구가 둘 다 나온다
                @Test
                void 등록_비운항목_모두_알림_400() throws Exception {
                        MvcResult r = mockMvc.perform(post("/wms/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body(null, 1, "SKU-T001", "", "", "BOX", null)))
                                        .andExpect(status().isBadRequest())
                                        .andReturn();
                        String msg = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
                        assertTrue(msg.contains("품목명은 필수입니다"));
                        assertTrue(msg.contains("규격은 필수입니다"));
                }

                @Test
                void 등록_잔여일_음수_400() throws Exception {
                        mockMvc.perform(post("/wms/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body(null, 1, "SKU-T001", "테스트품목", "120g*40", "BOX", -5)))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                void 등록_같은화주_같은코드_409() throws Exception {
                        등록(1, "SKU-T001");
                        mockMvc.perform(post("/wms/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(등록본문(1, "SKU-T001")))
                                        .andExpect(status().isConflict());
                }

                @Test
                void 개별조회_없는번호_404() throws Exception {
                        mockMvc.perform(get("/wms/product/detail").param("productid", "9999"))
                                        .andExpect(status().isNotFound());
                }

                @Test
                void 수정_없는번호_404() throws Exception {
                        mockMvc.perform(put("/wms/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body(9999, 1, "SKU-T001", "테스트품목", "120g*40", "BOX", null)))
                                        .andExpect(status().isNotFound());
                }

                // 화주는 바꿀 수 없다
                @Test
                void 수정_화주변경_400() throws Exception {
                        int id = 등록(1, "SKU-T001");
                        mockMvc.perform(put("/wms/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body(id, 2, "SKU-T001", "테스트품목", "120g*40", "BOX", null)))
                                        .andExpect(status().isBadRequest());
                }

                // 같은 화주의 다른 품목 코드로 바꾸면 중복
                @Test
                void 수정_같은화주_다른품목코드와_중복_409() throws Exception {
                        등록(1, "SKU-T001");
                        int idB = 등록(1, "SKU-T002");
                        mockMvc.perform(put("/wms/product")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body(idB, 1, "SKU-T001", "테스트품목", "120g*40", "BOX", null)))
                                        .andExpect(status().isConflict());
                }
        }
}