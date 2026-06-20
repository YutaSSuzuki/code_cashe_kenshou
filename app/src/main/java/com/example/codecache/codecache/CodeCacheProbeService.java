package com.example.codecache.codecache;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("code-cache-test")
public class CodeCacheProbeService {

    public long hot(int count) {
        long result = 0x13579BDF2468ACE0L;

        for (int i = 1; i <= count; i++) {
            result = result * 31 + i;
            result ^= result >>> 7;
        }

        return result;
    }

    public long cold(int count) {
        long result = 0x13579BDF2468ACE0L;

        for (int i = 1; i <= count; i++) {
            result = result * 31 + i;
            result ^= result >>> 7;
        }

        return result;
    }
}
