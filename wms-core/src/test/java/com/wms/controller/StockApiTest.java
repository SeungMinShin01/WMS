package com.wms.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
public class StockApiTest {

    @Autowired
    MockMvc mockMvc;

    @Nested
    class 성공 {

        @Test
        void 재고목록_13줄_소비기한순() throws Exception {
            mockMvc.perform(get("/wms/stocks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(13)))
                    .andExpect(jsonPath("$[0].expiryDate").value("2027-01-05"));
        }

        @Test
        void 가용수량_재고빼기할당() throws Exception {
            mockMvc.perform(get("/wms/stocks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.stockId == 3)].availableQty", hasItem(20)));
        }

        @Test
        void 적재하면_재고줄_늘어남() throws Exception {
            mockMvc.perform(put("/wms/inspections")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"detailId\": 14, \"locationId\": 12}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/wms/stocks"))
                    .andExpect(jsonPath("$", hasSize(14)))
                    .andExpect(jsonPath("$[?(@.locationCode == 'B-02-02')].qty", hasItem(80)));
        }
    }

}
