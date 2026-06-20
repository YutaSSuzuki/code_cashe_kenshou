package com.example.codecache.codecache;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/code-cache")
@Profile("code-cache-test")
public class CodeCacheTestController {

    private static final int MAX_CLASSES_PER_REQUEST = 1_000;
    private static final int MAX_TOTAL_CLASSES = 20_000;
    private static final int MAX_WARMUP_ITERATIONS = 20_000;
    private static final long MAX_INVOCATIONS_PER_REQUEST = 20_000_000L;
    private static final int MIN_PROBE_COUNT = 1;
    private static final int MAX_PROBE_COUNT = 10_000_000;

    private final CodeCacheGeneratorService generatorService;
    private final CodeCacheProbeService probeService;

    public CodeCacheTestController(
            CodeCacheGeneratorService generatorService,
            CodeCacheProbeService probeService
    ) {
        this.generatorService = generatorService;
        this.probeService = probeService;
    }

    @PostMapping("/generator/generate")
    public synchronized GeneratorResult generate(
            @RequestParam(defaultValue = "250") int classes,
            @RequestParam(defaultValue = "10000") int warmupIterations
    ) {
        validateRange("classes", classes, 1, MAX_CLASSES_PER_REQUEST);
        validateRange("warmupIterations", warmupIterations, 1, MAX_WARMUP_ITERATIONS);
        validateInvocations(classes, warmupIterations);

        int totalAfterGeneration = generatorService.status().totalGenerated() + classes;
        if (totalAfterGeneration > MAX_TOTAL_CLASSES) {
            throw badRequest("total generated classes must not exceed " + MAX_TOTAL_CLASSES);
        }

        return generatorService.generate(classes, warmupIterations);
    }

    @PostMapping("/generator/warm")
    public GeneratorResult warm(
            @RequestParam(defaultValue = "100") int iterations
    ) {
        validateRange("iterations", iterations, 1, MAX_WARMUP_ITERATIONS);

        int totalGenerated = generatorService.status().totalGenerated();
        if (totalGenerated == 0) {
            throw badRequest("generate at least one class before warm-up");
        }

        validateInvocations(totalGenerated, iterations);
        return generatorService.warmAll(iterations);
    }

    @GetMapping("/generator/status")
    public GeneratorStatus status() {
        return generatorService.status();
    }

    @GetMapping("/probe/hot")
    public ProbeResponse hot(
            @RequestParam(defaultValue = "100000") int count
    ) {
        validateRange("count", count, MIN_PROBE_COUNT, MAX_PROBE_COUNT);
        long startedAt = System.nanoTime();
        long result = probeService.hot(count);
        return new ProbeResponse("hot", count, result, System.nanoTime() - startedAt);
    }

    @GetMapping("/probe/cold")
    public ProbeResponse cold(
            @RequestParam(defaultValue = "100000") int count
    ) {
        validateRange("count", count, MIN_PROBE_COUNT, MAX_PROBE_COUNT);
        long startedAt = System.nanoTime();
        long result = probeService.cold(count);
        return new ProbeResponse("cold", count, result, System.nanoTime() - startedAt);
    }

    private void validateInvocations(int units, int iterations) {
        long invocations = (long) units * iterations;
        if (invocations > MAX_INVOCATIONS_PER_REQUEST) {
            throw badRequest("invocations per request must not exceed " + MAX_INVOCATIONS_PER_REQUEST);
        }
    }

    private void validateRange(String name, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw badRequest("%s must be between %d and %d".formatted(name, minimum, maximum));
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
