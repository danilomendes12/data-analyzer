package com.danilomendes.dataanalyzer.io;

import com.danilomendes.dataanalyzer.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Teste unitário do despacho de eventos do watcher ({@code handleEvent}), sem subir o Spring nem um
 * WatchService real. Cobre o ramo de OVERFLOW (→ nova varredura) e o filtro de submissão (só {@code .dat}
 * regular e ainda não processado), usando um {@link WatchEvent} stub.
 */
class DirectoryWatcherEventTest {

    @TempDir
    Path inputDir;
    @TempDir
    Path outputDir;

    private InitialScanner initialScanner;
    private ProcessedFileChecker checker;
    private FileTaskSubmitter submitter;
    private DirectoryWatcher watcher;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(inputDir, outputDir);
        initialScanner = mock(InitialScanner.class);
        checker = mock(ProcessedFileChecker.class);
        submitter = mock(FileTaskSubmitter.class);
        watcher = new DirectoryWatcher(properties, initialScanner, checker, submitter,
            new OutputPathResolver(properties));
    }

    @Test
    void stopIsANoOpWhenNeverStarted() {
        // stop() sem run(): watchService e watchThread são null; os ramos de guarda evitam NPE.
        assertThatCode(() -> watcher.stop()).doesNotThrowAnyException();
    }

    @Test
    void overflowTriggersRescanAndSubmitsNothing() {
        watcher.handleEvent(stubEvent(StandardWatchEventKinds.OVERFLOW, null));

        verify(initialScanner).scan();
        verifyNoInteractions(submitter);
    }

    @Test
    void submitsUnprocessedDatFile() throws IOException {
        Path file = Files.createFile(inputDir.resolve("vendas.dat"));
        when(checker.isProcessed(file)).thenReturn(false);

        watcher.handleEvent(stubEvent(StandardWatchEventKinds.ENTRY_CREATE, Path.of("vendas.dat")));

        verify(submitter).submit(file);
    }

    @Test
    void ignoresNonDatFile() throws IOException {
        Files.createFile(inputDir.resolve("notas.txt"));

        watcher.handleEvent(stubEvent(StandardWatchEventKinds.ENTRY_CREATE, Path.of("notas.txt")));

        verifyNoInteractions(submitter);
    }

    @Test
    void ignoresAlreadyProcessedDatFile() throws IOException {
        Path file = Files.createFile(inputDir.resolve("vendas.dat"));
        when(checker.isProcessed(file)).thenReturn(true);

        watcher.handleEvent(stubEvent(StandardWatchEventKinds.ENTRY_CREATE, Path.of("vendas.dat")));

        verifyNoInteractions(submitter);
    }

    private static WatchEvent<?> stubEvent(WatchEvent.Kind<?> kind, Path context) {
        WatchEvent<?> event = mock(WatchEvent.class);
        doReturn(kind).when(event).kind();
        doReturn(context).when(event).context();
        return event;
    }
}
