package com.danilomendes.dataanalyzer.io;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ponto único de submissão de arquivos ao pool, com deduplicação em voo. A varredura inicial e o watcher
 * chamam {@link #submit(Path)}; o {@code ConcurrentHashMap.newKeySet()} garante que um mesmo arquivo
 * (visto pela varredura e por um evento, ou por vários {@code ENTRY_MODIFY}) seja processado uma vez só.
 *
 * <p>A checagem de estabilidade roda aqui, dentro da tarefa (e não no loop de eventos), por dois motivos:
 * mantém o loop fino e não bloqueante, e cobre também a varredura de subida — um arquivo sendo copiado no
 * exato momento do boot não é meio-processado.
 */
@Component
public class FileTaskSubmitter {

    private static final Logger log = LoggerFactory.getLogger(FileTaskSubmitter.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final FileProcessor processor;
    private final StableFileDetector stableFileDetector;
    private final ExecutorService pool;
    // Chaves normalizadas (absolutas) para que caminhos equivalentes não escapem da deduplicação.
    private final Set<Path> inFlight = ConcurrentHashMap.newKeySet();

    public FileTaskSubmitter(FileProcessor processor, StableFileDetector stableFileDetector) {
        this.processor = processor;
        this.stableFileDetector = stableFileDetector;
        // Pool fixo pelo nº de CPUs; fila ilimitada de propósito (cada tarefa carrega só um Path).
        this.pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), namedThreadFactory());
    }

    public void submit(Path path) {
        Path key = path.toAbsolutePath().normalize();
        if (!inFlight.add(key)) {
            log.debug("Arquivo já em processamento, ignorando submissão duplicada: {}", key);
            return;
        }
        pool.execute(() -> runTask(key));
    }

    private void runTask(Path key) {
        try {
            if (stableFileDetector.isStable(key)) {
                processor.process(key);
            } else {
                log.error("Arquivo instável (ainda em escrita?), desistindo: {} — será recuperado na próxima subida", key);
            }
        } finally {
            inFlight.remove(key);
        }
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Pool não terminou em {}s; forçando encerramento", SHUTDOWN_TIMEOUT_SECONDS);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory namedThreadFactory() {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "file-processor-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
