package com.danilomendes.dataanalyzer.io;

import com.danilomendes.dataanalyzer.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InitialScannerTest {

    @TempDir
    Path inputDir;
    @TempDir
    Path outputDir;

    private FileTaskSubmitter submitter;
    private InitialScanner scanner;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(inputDir, outputDir);
        OutputPathResolver resolver = new OutputPathResolver(properties);
        submitter = mock(FileTaskSubmitter.class);
        scanner = new InitialScanner(properties, resolver, new ProcessedFileChecker(resolver), submitter);
    }

    @Test
    void submitsOnlyUnprocessedDatFiles() throws IOException {
        Path pending = inputDir.resolve("pending.dat");
        Files.createFile(pending);
        Files.createFile(inputDir.resolve("done.dat"));
        Files.createFile(outputDir.resolve("done.done.dat")); // já processado → deve ser pulado
        Files.createFile(inputDir.resolve("notes.txt"));       // não .dat → deve ser ignorado

        scanner.scan();

        verify(submitter, only()).submit(pending);
    }

    @Test
    void submitsNothingWhenInputDirIsEmpty() {
        scanner.scan();

        verifyNoInteractions(submitter);
    }
}
