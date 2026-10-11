package com.wms.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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

import java.util.List;

import org.springframework.test.web.servlet.MvcResult;
import com.jayway.jsonpath.JsonPath;

// sample2 기준
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

                // 신라면 50 : LOT-01 과 LOT-03 이 소비기한·가용(70) 같음 → LOT 적재일 빠른 LOT-01
                // → 칸 가용 많은 A-01-02 40 → A-01-01 10. 결과는 로케이션 코드순
                @Test
                void 미리보기_할당규칙_신라면() throws Exception {
                        mockMvc.perform(get("/wms/allocations/10/preview").param("documentItemIds", "21"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(2)))
                                        .andExpect(jsonPath("$[0].locationCode").value("A-01-01"))
                                        .andExpect(jsonPath("$[0].qty").value(10))
                                        .andExpect(jsonPath("$[1].locationCode").value("A-01-02"))
                                        .andExpect(jsonPath("$[1].qty").value(40));
                }

                // 품목을 고르지 않으면 문서 전체 (신라면 2줄 + 센트룸 B-01-02 1줄)
                @Test
                void 미리보기_전체품목() throws Exception {
                        mockMvc.perform(get("/wms/allocations/10/preview"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(3)))
                                        .andExpect(jsonPath("$[2].locationCode").value("B-01-02"))
                                        .andExpect(jsonPath("$[2].qty").value(5));
                }

                // 같은 LOT · 같은 가용 → 칸 적재일 빠른 B-01-01 20 → B-02-01 10
                @Test
                void 미리보기_칸적재일순_참치() throws Exception {
                        mockMvc.perform(get("/wms/allocations/15/preview"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(2)))
                                        .andExpect(jsonPath("$[0].locationCode").value("B-01-01"))
                                        .andExpect(jsonPath("$[0].qty").value(20))
                                        .andExpect(jsonPath("$[1].locationCode").value("B-02-01"))
                                        .andExpect(jsonPath("$[1].qty").value(10));
                }

                // 일부 품목만 할당 → 201, 문서 ALLOCATED, 그 줄 할당수량 50
                @Test
                void 피킹리스트생성_일부품목_할당() throws Exception {
                        mockMvc.perform(post("/wms/allocations/10/pickinglist")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("[{\"documentItemId\":21,\"stockId\":2,\"qty\":40},"
                                                        + " {\"documentItemId\":21,\"stockId\":1,\"qty\":10}]"))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$", hasSize(2)));

                        mockMvc.perform(get("/wms/outbounds/10"))
                                        .andExpect(jsonPath("$.status").value("ALLOCATED"))
                                        .andExpect(jsonPath("$.items[0].allocatedQty").value(50))
                                        .andExpect(jsonPath("$.items[1].allocatedQty").value(0));
                }

                // 전 품목 할당 → 지금 규칙은 PICKING
                // 전 품목 할당 → 문서는 ALLOCATED 유지 (ED-52: 첫 줄을 집을 때 PICKING)
                @Test
                void 피킹리스트생성_전품목_할당() throws Exception {
                        mockMvc.perform(post("/wms/allocations/10/pickinglist")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("[{\"documentItemId\":21,\"stockId\":2,\"qty\":40},"
                                                        + " {\"documentItemId\":21,\"stockId\":1,\"qty\":10},"
                                                        + " {\"documentItemId\":22,\"stockId\":8,\"qty\":5}]"))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$", hasSize(3)));

                        mockMvc.perform(get("/wms/outbounds/10"))
                                        .andExpect(jsonPath("$.status").value("ALLOCATED"));
                }

                // 할당 → 줄마다 피킹 확인 → 출고확정 : 문서 SHIPPED, 실물·선점 같이 감소
                @Test
                void 출고확정_재고차감() throws Exception {
                        // 1. 할당 → 응답으로 피킹 줄 2개를 받는다
                        MvcResult allocated = mockMvc.perform(post("/wms/allocations/15/pickinglist")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("[{\"documentItemId\":29,\"stockId\":4,\"qty\":20},"
                                                        + " {\"documentItemId\":29,\"stockId\":13,\"qty\":10}]"))
                                        .andExpect(status().isCreated())
                                        .andReturn();

                        // 2. 피킹 확인 (ED-52) : 응답의 detailId 마다 집음(PICKED) 처리
                        List<Integer> detailIds = JsonPath.read(
                                        allocated.getResponse().getContentAsString(), "$[*].detailId");
                        for (Integer detailId : detailIds) {
                                mockMvc.perform(put("/wms/pickings/" + detailId))
                                                .andExpect(status().isOk());
                        }

                        // 3. 출고확정
                        mockMvc.perform(put("/wms/outbounds/15/ship"))
                                        .andExpect(status().isOk())
                                        .andExpect(content().string("SHIPPED"));

                        mockMvc.perform(get("/wms/stocks"))
                                        .andExpect(jsonPath("$[?(@.stockId == 4)].qty", contains(0)))
                                        .andExpect(jsonPath("$[?(@.stockId == 13)].qty", contains(10)))
                                        .andExpect(jsonPath("$[?(@.stockId == 13)].allocatedQty", contains(0)));
                }

                // 할당 문서 취소 → 선점 해제, 피킹 줄 삭제
                @Test
                void 취소_할당풀기() throws Exception {
                        mockMvc.perform(put("/wms/outbounds/9/cancel"))
                                        .andExpect(status().isOk())
                                        .andExpect(content().string("CANCELED"));

                        mockMvc.perform(get("/wms/allocations/9"))
                                        .andExpect(jsonPath("$", hasSize(0)));

                        mockMvc.perform(get("/wms/stocks"))
                                        .andExpect(jsonPath("$[?(@.stockId == 3)].allocatedQty", contains(0)))
                                        .andExpect(jsonPath("$[?(@.stockId == 6)].allocatedQty", contains(0)));
                }
        }

        @Nested
        class 실패 {

                @Test
                void 출고상세_없는문서_404() throws Exception {
                        mockMvc.perform(get("/wms/outbounds/999"))
                                        .andExpect(status().isNotFound());
                }

                @Test
                void 출고상세_입고문서_400() throws Exception {
                        mockMvc.perform(get("/wms/outbounds/1"))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                void 피킹리스트조회_없는문서_404() throws Exception {
                        mockMvc.perform(get("/wms/allocations/999"))
                                        .andExpect(status().isNotFound());
                }

                @Test
                void 미리보기_재고부족_409() throws Exception {
                        mockMvc.perform(get("/wms/allocations/11/preview").param("documentItemIds", "23"))
                                        .andExpect(status().isConflict());
                }

                @Test
                void 미리보기_출고완료문서_409() throws Exception {
                        mockMvc.perform(get("/wms/allocations/8/preview"))
                                        .andExpect(status().isConflict());
                }

                @Test
                void 미리보기_입고문서_400() throws Exception {
                        mockMvc.perform(get("/wms/allocations/1/preview"))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                void 미리보기_다른문서품목_400() throws Exception {
                        mockMvc.perform(get("/wms/allocations/10/preview").param("documentItemIds", "28"))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                void 미리보기_이미할당된품목_409() throws Exception {
                        mockMvc.perform(get("/wms/allocations/9/preview").param("documentItemIds", "19"))
                                        .andExpect(status().isConflict());
                }

                @Test
                void 피킹리스트생성_빈요청_400() throws Exception {
                        mockMvc.perform(post("/wms/allocations/10/pickinglist")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("[]"))
                                        .andExpect(status().isBadRequest());
                }

                // 신라면 주문 50 인데 30 만 보냄
                @Test
                void 피킹리스트생성_수량불일치_400() throws Exception {
                        mockMvc.perform(post("/wms/allocations/10/pickinglist")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("[{\"documentItemId\":21,\"stockId\":2,\"qty\":30}]"))
                                        .andExpect(status().isBadRequest());
                }

                // stock 1 가용 30 에 50 을 요청
                @Test
                void 피킹리스트생성_가용초과_409() throws Exception {
                        mockMvc.perform(post("/wms/allocations/10/pickinglist")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("[{\"documentItemId\":21,\"stockId\":1,\"qty\":50}]"))
                                        .andExpect(status().isConflict());
                }

                // 신라면 줄에 센트룸 재고(stock 8)
                @Test
                void 피킹리스트생성_다른상품재고_400() throws Exception {
                        mockMvc.perform(post("/wms/allocations/10/pickinglist")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("[{\"documentItemId\":21,\"stockId\":8,\"qty\":50}]"))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                void 피킹리스트생성_출고완료문서_409() throws Exception {
                        mockMvc.perform(post("/wms/allocations/8/pickinglist")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("[{\"documentItemId\":17,\"stockId\":1,\"qty\":30}]"))
                                        .andExpect(status().isConflict());
                }

                // 할당(ALLOCATED) 문서는 아직 피킹 전 → 출고확정 불가
                @Test
                void 출고확정_할당문서_409() throws Exception {
                        mockMvc.perform(put("/wms/outbounds/9/ship"))
                                        .andExpect(status().isConflict());
                }

                @Test
                void 출고확정_이미출고_409() throws Exception {
                        mockMvc.perform(put("/wms/outbounds/8/ship"))
                                        .andExpect(status().isConflict());
                }

                @Test
                void 출고확정_없는문서_404() throws Exception {
                        mockMvc.perform(put("/wms/outbounds/999/ship"))
                                        .andExpect(status().isNotFound());
                }

                @Test
                void 취소_출고완료_409() throws Exception {
                        mockMvc.perform(put("/wms/outbounds/8/cancel"))
                                        .andExpect(status().isConflict());
                }

                @Test
                void 취소_이미취소_409() throws Exception {
                        mockMvc.perform(put("/wms/outbounds/12/cancel"))
                                        .andExpect(status().isConflict());
                }
        }
}