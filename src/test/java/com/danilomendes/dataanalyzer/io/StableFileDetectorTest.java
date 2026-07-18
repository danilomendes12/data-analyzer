package com.danilomendes.dataanalyzer.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StableFileDetectorTest {

    @TempDir
    Path dir;

    private final StableFileDetector detector = new StableFileDetector();

    @Test
    void reportsStableForAFullyWrittenFile() throws IOException {
        Path file = dir.resolve("vendas.dat");
        Files.writeString(file, "conteúdo completo", StandardCharsets.UTF_8);

        assertThat(detector.isStable(file)).isTrue();
    }

    @Test
    void reportsUnstableForAMissingFile() {
        assertThat(detector.isStable(dir.resolve("ausente.dat"))).isFalse();
    }
}
