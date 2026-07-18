package com.danilomendes.dataanalyzer.io;

import com.danilomendes.dataanalyzer.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessedFileCheckerTest {

    @TempDir
    Path inputDir;
    @TempDir
    Path outputDir;

    private ProcessedFileChecker checker;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(inputDir, outputDir);
        checker = new ProcessedFileChecker(new OutputPathResolver(properties));
    }

    @Test
    void reportsNotProcessedWhenDoneFileIsAbsent() {
        assertThat(checker.isProcessed(inputDir.resolve("vendas.dat"))).isFalse();
    }

    @Test
    void reportsProcessedWhenDoneFileExists() throws IOException {
        Files.createFile(outputDir.resolve("vendas.done.dat"));

        assertThat(checker.isProcessed(inputDir.resolve("vendas.dat"))).isTrue();
    }
}
