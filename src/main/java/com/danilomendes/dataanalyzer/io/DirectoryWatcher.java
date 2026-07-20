package com.danilomendes.dataanalyzer.io;

import com.danilomendes.dataanalyzer.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

/**
 * Bootstrap e monitoramento do diretório de entrada via {@link ApplicationRunner} + {@code @PreDestroy}
 * (em vez de {@code SmartLifecycle}): o desligamento ordenado vem da ordem de destruição de beans — o watcher
 * depende do {@link FileTaskSubmitter}, logo é destruído antes dele e só então o pool é drenado.
 *
 * <p>O {@link WatchService} é registrado ANTES da varredura inicial: um arquivo que caia entre os dois passos
 * gera evento e não se perde; a deduplicação do submitter evita processá-lo duas vezes.
 */
@Component
public class DirectoryWatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DirectoryWatcher.class);
    private static final long JOIN_TIMEOUT_MILLIS = 5_000;

    private final AppProperties properties;
    private final InitialScanner initialScanner;
    private final ProcessedFileChecker checker;
    private final FileTaskSubmitter submitter;
    private final OutputPathResolver outputPathResolver;

    private volatile boolean running;
    private WatchService watchService;
    private Thread watchThread;

    public DirectoryWatcher(AppProperties properties, InitialScanner initialScanner,
                            ProcessedFileChecker checker, FileTaskSubmitter submitter,
                            OutputPathResolver outputPathResolver) {
        this.properties = properties;
        this.initialScanner = initialScanner;
        this.checker = checker;
        this.submitter = submitter;
        this.outputPathResolver = outputPathResolver;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Path inputDir = properties.inputDir();
        Files.createDirectories(inputDir);
        Files.createDirectories(properties.outputDir());

        watchService = inputDir.getFileSystem().newWatchService();
        inputDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
        initialScanner.scan();

        running = true;
        watchThread = new Thread(this::watchLoop, "dir-watcher");
        // Não-daemon de propósito: o loop de monitoramento é a razão de a aplicação continuar viva.
        // Como app não-web, sem uma thread não-daemon o JVM encerraria assim que main() retornasse.
        // No shutdown, stop() fecha o WatchService, o take() lança e a thread termina, liberando o JVM.
        watchThread.setDaemon(false);
        watchThread.start();
        log.info("Monitorando o diretório de entrada {}", inputDir);
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (ClosedWatchServiceException e) {
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                try {
                    handleEvent(event);
                } catch (RuntimeException e) {
                    // Blindagem do loop: nenhuma exceção inesperada no tratamento de um evento (ex.:
                    // RejectedExecutionException ao submeter durante o shutdown) pode derrubar a thread
                    // dir-watcher — não-daemon, é a razão de o JVM seguir vivo.
                    log.error("Falha ao tratar evento de watch {}", event.context(), e);
                }
            }
            if (!key.reset()) {
                log.warn("WatchKey inválida para {}; encerrando o loop de eventos", properties.inputDir());
                return;
            }
        }
    }

    void handleEvent(WatchEvent<?> event) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            log.warn("Watch OVERFLOW: eventos podem ter sido perdidos, reexecutando a varredura completa");
            initialScanner.scan();
            return;
        }
        Path name = (Path) event.context();
        Path path = properties.inputDir().resolve(name);
        // Filtro fino: só arquivo de entrada (.dat regular) e só o que ainda não foi processado
        // (ignora ENTRY_MODIFY tardio de arquivo já pronto).
        if (outputPathResolver.isInputFile(path) && !checker.isProcessed(path)) {
            submitter.submit(path);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Falha ao fechar o WatchService", e);
            }
        }
        if (watchThread != null) {
            try {
                watchThread.join(JOIN_TIMEOUT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
