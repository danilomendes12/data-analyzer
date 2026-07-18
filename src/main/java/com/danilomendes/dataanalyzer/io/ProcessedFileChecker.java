package com.danilomendes.dataanalyzer.io;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Único predicado "já processado" do sistema: um arquivo está processado sse, e somente se, o seu
 * arquivo de saída já existe. Usado tanto pela varredura inicial quanto pelo watcher (para ignorar um
 * {@code ENTRY_MODIFY} tardio de arquivo já processado), sempre pela mesma regra de nome.
 */
@Component
public class ProcessedFileChecker {

    private final OutputPathResolver outputPathResolver;

    public ProcessedFileChecker(OutputPathResolver outputPathResolver) {
        this.outputPathResolver = outputPathResolver;
    }

    public boolean isProcessed(Path input) {
        return Files.exists(outputPathResolver.outputPathFor(input));
    }
}
