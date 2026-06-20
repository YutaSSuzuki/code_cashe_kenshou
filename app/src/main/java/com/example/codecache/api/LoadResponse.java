package com.example.codecache.api;

public record LoadResponse(
        int count,
        long result,
        long elapsedNanos
) {
}