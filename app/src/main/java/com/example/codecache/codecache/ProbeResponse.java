package com.example.codecache.codecache;

public record ProbeResponse(
        String probe,
        int count,
        long result,
        long elapsedNanos
) {
}
