package com.danilomendes.dataanalyzer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@SpringBootTest
class ApplicationStartupTest {

    static Path baseDir;

    // Aponta os diretórios para um temp isolado: o watcher sobe no contexto e não deve tocar em ~/data.
    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) throws IOException {
        baseDir = Files.createTempDirectory("data-analyzer-startup");
        registry.add("app.input-dir", () -> baseDir.resolve("in").toString());
        registry.add("app.output-dir", () -> baseDir.resolve("out").toString());
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (baseDir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(baseDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Melhor esforço.
                }
            });
        }
    }

    @Test
    void contextLoads() {
    }
}
