package com.example.codecache.codecache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodeCacheProbeServiceTest {

    private final CodeCacheProbeService probeService = new CodeCacheProbeService();

    @Test
    void hotAndColdPerformEquivalentCalculations() {
        assertEquals(probeService.hot(1_000), probeService.cold(1_000));
    }
}
