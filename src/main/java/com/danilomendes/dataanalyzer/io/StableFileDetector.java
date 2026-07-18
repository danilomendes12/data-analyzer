package com.danilomendes.dataanalyzer.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detecta se um arquivo terminou de ser escrito comparando dois tamanhos consecutivos: considera estável
 * quando duas leituras seguidas (separadas por {@link #POLL_INTERVAL_MILLIS}) são iguais. Guarda contra
 * processar um arquivo ainda em escrita — tanto num evento do watcher quanto na varredura de subida, caso
 * um arquivo esteja sendo copiado bem na hora do boot.
 */
@Component
public class StableFileDetector {

    private static final Logger log = LoggerFactory.getLogger(StableFileDetector.class);

    // 5 tentativas × 200 ms = até ~800 ms de espera; suficiente para uma cópia local terminar,
    // curto o bastante para não segurar o pool. Ao estourar, desistimos (o arquivo volta na próxima subida).
    private static final int MAX_ATTEMPTS = 5;
    private static final long POLL_INTERVAL_MILLIS = 200;

    public boolean isStable(Path path) {
        long previousSize = -1;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            long currentSize;
            try {
                if (!Files.exists(path)) {
                    return false;
                }
                currentSize = Files.size(path);
            } catch (IOException e) {
                log.warn("Falha ao medir o tamanho de {}", path, e);
                return false;
            }
            if (currentSize == previousSize) {
                return true;
            }
            previousSize = currentSize;
            if (!sleep()) {
                return false;
            }
        }
        return false;
    }

    private boolean sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
