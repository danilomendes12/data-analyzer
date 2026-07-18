package com.danilomendes.dataanalyzer.io;

import com.danilomendes.dataanalyzer.config.AppProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Fonte única da regra "arquivo de entrada → arquivo de saída". Vive num componente próprio (e não
 * dentro do writer) porque tanto o {@link ReportWriter} quanto o {@link ProcessedFileChecker} dependem
 * dela: se cada um tivesse sua própria cópia, o mecanismo de skip da varredura quebraria em silêncio
 * assim que as duas divergissem.
 */
@Component
public class OutputPathResolver {

    private static final String INPUT_SUFFIX = ".dat";
    private static final String OUTPUT_SUFFIX = ".done.dat";

    private final Path outputDir;

    public OutputPathResolver(AppProperties properties) {
        this.outputDir = properties.outputDir();
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
