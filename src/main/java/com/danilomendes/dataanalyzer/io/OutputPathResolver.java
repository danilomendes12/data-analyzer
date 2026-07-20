package com.danilomendes.dataanalyzer.io;

import com.danilomendes.dataanalyzer.config.AppProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fonte única da convenção de nomes de arquivo: reconhece um {@code .dat} de entrada e resolve o seu
 * {@code .done.dat} de saída. Vive num componente próprio (e não dentro do writer) porque tanto o
 * {@link ReportWriter} quanto o {@link ProcessedFileChecker} dependem da regra de nome de saída; e o
 * literal {@code ".dat"} de entrada mora aqui, num lugar só, para o watcher e a varredura reconhecerem
 * entrada pelo mesmo critério (se cada um tivesse sua cópia, o mecanismo de skip quebraria em silêncio
 * no dia em que divergissem).
 */
@Component
public class OutputPathResolver {

    private static final String INPUT_SUFFIX = ".dat";
    private static final String OUTPUT_SUFFIX = ".done.dat";

    private final Path outputDir;

    public OutputPathResolver(AppProperties properties) {
        this.outputDir = properties.outputDir();
    }

    public boolean isInputFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(INPUT_SUFFIX);
    }

    // vendas.dat -> <out>/vendas.done.dat: troca a extensão em vez de concatenar (evita vendas.dat.done.dat).
    public Path outputPathFor(Path input) {
        String fileName = input.getFileName().toString();
        String base = fileName.endsWith(INPUT_SUFFIX)
            ? fileName.substring(0, fileName.length() - INPUT_SUFFIX.length())
            : fileName;
        return outputDir.resolve(base + OUTPUT_SUFFIX);
    }
}
