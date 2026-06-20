package com.example.codecache.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LoadServiceTest {

    private final LoadService loadService = new LoadService();

    @Test
    void sameCountReturnsSameResult() {
        long first = loadService.calculate(1_000);
        long second = loadService.calculate(1_000);

        assertEquals(first, second);
    }

    @Test
    void differentCountReturnsDifferentResult() {
        long first = loadService.calculate(1);
        long second = loadService.calculate(2);

        assertNotEquals(first, second);
    }
}