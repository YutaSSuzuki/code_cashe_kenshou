package com.example.codecache.codecache;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.description.modifier.Visibility;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

@Service
@Profile("code-cache-test")
public class CodeCacheGeneratorService {

    private static final String GENERATED_PACKAGE = "com.example.codecache.generated";

    private final List<GeneratedWorkUnit> retainedUnits = new ArrayList<>();
    private int nextClassId = 1;
    private volatile long sink;

    public synchronized GeneratorResult generate(int classes, int warmupIterations) {
        long startedAt = System.nanoTime();
        List<GeneratedWorkUnit> generated = new ArrayList<>(classes);

        for (int i = 0; i < classes; i++) {
            int classId = nextClassId++;
            GeneratedWorkUnit unit = createWorkUnit(classId);
            generated.add(unit);
            retainedUnits.add(unit);
        }

        long invocations = warm(generated, warmupIterations);
        return result("generate", classes, warmupIterations, invocations, startedAt);
    }

    public synchronized GeneratorResult warmAll(int iterations) {
        long startedAt = System.nanoTime();
        long invocations = warm(retainedUnits, iterations);
        return result("warm", 0, iterations, invocations, startedAt);
    }

    public synchronized GeneratorStatus status() {
        return new GeneratorStatus(retainedUnits.size(), sink);
    }

    private GeneratedWorkUnit createWorkUnit(int classId) {
        String className = "%s.WorkUnit%08d".formatted(GENERATED_PACKAGE, classId);

        try {
            Class<? extends GeneratedWorkUnit> generatedType = new ByteBuddy()
                    .subclass(Object.class)
                    .implement(GeneratedWorkUnit.class)
                    .name(className)
                    .defineMethod("execute", long.class, Visibility.PUBLIC)
                    .withParameters(int.class)
                    .intercept(FixedValue.value((long) classId))
                    .make()
                    .load(
                            GeneratedWorkUnit.class.getClassLoader(),
                            ClassLoadingStrategy.Default.INJECTION
                    )
                    .getLoaded()
                    .asSubclass(GeneratedWorkUnit.class);

            return generatedType.getDeclaredConstructor().newInstance();
        } catch (InstantiationException
                 | IllegalAccessException
                 | InvocationTargetException
                 | NoSuchMethodException exception) {
            throw new IllegalStateException("Failed to instantiate generated work unit " + className, exception);
        }
    }

    private long warm(List<GeneratedWorkUnit> units, int iterations) {
        long localSink = sink;

        for (int iteration = 0; iteration < iterations; iteration++) {
            int input = iteration + 1;
            for (GeneratedWorkUnit unit : units) {
                localSink ^= unit.execute(input) + input;
            }
        }

        sink = localSink;
        return (long) units.size() * iterations;
    }

    private GeneratorResult result(
            String operation,
            int generated,
            int iterations,
            long invocations,
            long startedAt
    ) {
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        return new GeneratorResult(
                operation,
                generated,
                retainedUnits.size(),
                iterations,
                invocations,
                elapsedMillis,
                sink
        );
    }
}
