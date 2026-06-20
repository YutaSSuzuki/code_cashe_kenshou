package com.example.codecache.service;

import org.springframework.stereotype.Service;

@Service
public class LoadService {

    public long calculate(int count) {
        long result = 1L;

        for (int i = 1; i <= count; i++) {
            result = result * 31 + i;
            result ^= result >>> 7;
        }

        return result;
    }
}