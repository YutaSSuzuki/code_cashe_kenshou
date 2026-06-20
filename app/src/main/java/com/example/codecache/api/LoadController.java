package com.example.codecache.api;

import com.example.codecache.service.LoadService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class LoadController {

    private static final int DEFAULT_COUNT = 100_000;
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 10_000_000;

    private final LoadService loadService;

    public LoadController(LoadService loadService) {
        this.loadService = loadService;
    }

    @GetMapping("/load")
    public LoadResponse load(
            @RequestParam(defaultValue = "100000") int count
    ) {
        validateCount(count);

        long startedAt = System.nanoTime();
        long result = loadService.calculate(count);
        long elapsedNanos = System.nanoTime() - startedAt;

        return new LoadResponse(count, result, elapsedNanos);
    }

    private void validateCount(int count) {
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "count must be between %d and %d"
                            .formatted(MIN_COUNT, MAX_COUNT)
            );
        }
    }
}