package com.danilomendes.dataanalyzer.io;

import com.danilomendes.dataanalyzer.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Varredura da Opção B: lista os {@code .dat} do diretório de entrada e submete ao pool apenas os que
 * ainda não têm arquivo de saída. É reutilizável — roda na subida (após registrar o WatchService) e
 * também no tratamento de {@code OVERFLOW} do watcher, sem introduzir nenhum mecanismo novo.
 */
@Component
public class InitialScanner {

    private static final Logger log = LoggerFactory.getLogger(InitialScanner.class);
    private static final String DAT_SUFFIX = ".dat";

    private final AppProperties properties;
    private final ProcessedFileChecker checker;
    private final FileTaskSubmitter submitter;

    public InitialScanner(AppProperties properties, ProcessedFileChecker checker, FileTaskSubmitter submitter) {
        this.properties = properties;
        this.checker = checker;
        this.submitter = submitter;
    }

    public void scan() {
        Path inputDir = properties.inputDir();
        try (Stream<Path> files = Files.list(inputDir)) {
            files.filter(Files::isRegularFile)
                .filter(InitialScanner::isDatFile)
                .filter(path -> !checker.isProcessed(path))
                .forEach(submitter::submit);
        } catch (IOException e) {
            log.error("Falha ao varrer o diretório de entrada {}", inputDir, e);
        }
    }

    private static boolean isDatFile(Path path) {
        return path.getFileName().toString().endsWith(DAT_SUFFIX);
    }
}
