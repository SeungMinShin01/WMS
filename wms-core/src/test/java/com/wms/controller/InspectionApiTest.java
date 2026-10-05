package com.wms.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
public class InspectionApiTest {

        @Autowired
        MockMvc mockMvc;

        @Nested
        class 성공 {
                @Test
                void 검수_성공_201() throws Exception {
                        mockMvc.perform(post("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"documentItemId\": 14, \"qty\": 10 }")) // 문서6 (미검수)
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$").isNumber());
                }

                @Test
                void 검수현황_문서4_2줄_적재전() throws Exception {
                        mockMvc.perform(get("/wms/inspections/4"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(2)))
                                        .andExpect(jsonPath("$[*].locationCode", everyItem(nullValue())));
                }

                @Test
                void 검수현황_문서6_0줄() throws Exception {
                        mockMvc.perform(get("/wms/inspections/6"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(0)));
                }

                @Test
                void 적재_200_위치반영() throws Exception {
                        mockMvc.perform(put("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"detailId\": 14, \"locationId\": 12}"))
                                        .andExpect(status().isOk())
                                        .andExpect(content().string("true"));

                        mockMvc.perform(get("/wms/inspections/4"))
                                        .andExpect(jsonPath("$[*].locationCode", hasItem("B-02-02")));
                }

                @Test
                void 적재_전부끝나면_문서완료() throws Exception {
                        mockMvc.perform(put("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"detailId\":14, \"locationId\": 12}"))
                                        .andExpect(status().isOk());

                        mockMvc.perform(put("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"detailId\": 15, \"locationId\": 13}"))
                                        .andExpect(status().isOk());

                        mockMvc.perform(get("/wms/inbounds/4"))
                                        .andExpect(jsonPath("$.status").value("COMPLETED"));
                }

        }

        @Nested
        class 실패 {
                @Test
                void 검수_수량0_400() throws Exception {
                        mockMvc.perform(post("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"documentItemId\": 14, \"qty\": 0}"))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                void 검수_수량없음_400() throws Exception {
                        mockMvc.perform(post("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"documentItemId\": 14}"))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                void 검수_출고문서품목_400() throws Exception {
                        mockMvc.perform(post("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"documentItemId\": 21, \"qty\": 10}"))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                void 검수_없는품목_404() throws Exception {
                        mockMvc.perform(post("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"documentItemId\": 9999, \"qty\": 10}"))
                                        .andExpect(status().isNotFound());
                }

                @Test
                void 검수_취소문서_409() throws Exception {
                        mockMvc.perform(post("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"documentItemId\": 16, \"qty\": 10}"))
                                        .andExpect(status().isConflict());
                }

                @Test
                void 검수_완료문서_409() throws Exception {
                        mockMvc.perform(post("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"documentItemId\": 1, \"qty\": 10}"))
                                        .andExpect(status().isConflict());
                }

                @Test
                void 적재_비활성로케이션_409() throws Exception {
                        mockMvc.perform(put("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"detailId\": 15, \"locationId\": 10}"))
                                        .andExpect(status().isConflict());
                }

                @Test
                void 적재_없는검수기록_404() throws Exception {
                        mockMvc.perform(put("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"detailId\": 9999, \"locationId\": 12}"))
                                        .andExpect(status().isNotFound());
                }

                @Test
                void 적재_없는로케이션_404() throws Exception {
                        mockMvc.perform(put("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"detailId\": 15, \"locationId\": 9999}"))
                                        .andExpect(status().isNotFound());
                }

                @Test
                void 적재_두번_409() throws Exception {
                        mockMvc.perform(put("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"detailId\": 14, \"locationId\": 12}"))
                                        .andExpect(status().isOk());
                        mockMvc.perform(put("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"detailId\": 14, \"locationId\": 13}"))
                                        .andExpect(status().isConflict());
                }

                @Test
                void 적재_취소문서_409() throws Exception {
                        mockMvc.perform(put("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"detailId\": 21, \"locationId\": 12}"))
                                        .andExpect(status().isConflict());
                }

                @Test
                void 적재_검수중문서_409() throws Exception {
                        mockMvc.perform(put("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"detailId\": 16, \"locationId\": 12}"))
                                        .andExpect(status().isConflict());
                }

                @Test
                void 적재_완료문서_409() throws Exception {
                        mockMvc.perform(put("/wms/inspections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"detailId\": 1, \"locationId\": 12}"))
                                        .andExpect(status().isConflict());
                }

                @Test
                void 추천_없는검수기록_404() throws Exception {
                        mockMvc.perform(get("/wms/inspections/recommend/9999"))
                                        .andExpect(status().isNotFound());
                }
        }
}
