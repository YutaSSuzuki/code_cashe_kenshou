package com.example.codecache.codecache;

public record GeneratorResult(
        String operation,
        int generated,
        int totalGenerated,
        int iterations,
        long invocations,
        long elapsedMillis,
        long sink
) {
}
