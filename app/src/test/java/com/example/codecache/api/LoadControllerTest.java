package com.example.codecache.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void explicitCountReturnsResult() throws Exception {
        mockMvc.perform(get("/load").param("count", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1000))
                .andExpect(jsonPath("$.result").isNumber())
                .andExpect(jsonPath("$.elapsedNanos").isNumber());
    }

    @Test
    void omittedCountUsesDefaultValue() throws Exception {
        mockMvc.perform(get("/load"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(100000));
    }

    @Test
    void zeroReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/load").param("count", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeValueReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/load").param("count", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overMaximumReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/load").param("count", "10000001"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericValueReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/load").param("count", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void codeCacheTestApiIsDisabledByDefault() throws Exception {
        mockMvc.perform(get("/code-cache/generator/status"))
                .andExpect(status().isNotFound());
    }
}
