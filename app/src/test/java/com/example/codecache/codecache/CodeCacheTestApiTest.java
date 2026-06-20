package com.example.codecache.codecache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("code-cache-test")
class CodeCacheTestApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatesAndRetainsWorkUnits() throws Exception {
        mockMvc.perform(post("/code-cache/generator/generate")
                        .param("classes", "2")
                        .param("warmupIterations", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("generate"))
                .andExpect(jsonPath("$.generated").value(2))
                .andExpect(jsonPath("$.totalGenerated").value(2))
                .andExpect(jsonPath("$.invocations").value(6));

        mockMvc.perform(get("/code-cache/generator/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGenerated").value(2));
    }

    @Test
    void rejectsTooManyInvocations() throws Exception {
        mockMvc.perform(post("/code-cache/generator/generate")
                        .param("classes", "1001")
                        .param("warmupIterations", "20000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hotAndColdProbesReturnEquivalentResults() throws Exception {
        mockMvc.perform(get("/code-cache/probe/hot").param("count", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.probe").value("hot"))
                .andExpect(jsonPath("$.count").value(1000))
                .andExpect(jsonPath("$.result").isNumber())
                .andExpect(jsonPath("$.elapsedNanos").isNumber());

        mockMvc.perform(get("/code-cache/probe/cold").param("count", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.probe").value("cold"))
                .andExpect(jsonPath("$.count").value(1000))
                .andExpect(jsonPath("$.result").isNumber())
                .andExpect(jsonPath("$.elapsedNanos").isNumber());
    }
}
