package com.danilomendes.dataanalyzer.io;

import com.danilomendes.dataanalyzer.analysis.DataAnalyzer;
import com.danilomendes.dataanalyzer.parser.ParserRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Processa um arquivo em passada única, com um {@link DataAnalyzer} novo por arquivo (estado isolado).
 * Qualquer falha é logada e contida — nunca propaga, para não derrubar a thread do pool.
 */
@Component
public class FileProcessor {

    private static final Logger log = LoggerFactory.getLogger(FileProcessor.class);

    private final ParserRegistry registry;
    private final ReportWriter reportWriter;

    public FileProcessor(ParserRegistry registry, ReportWriter reportWriter) {
        this.registry = registry;
        this.reportWriter = reportWriter;
    }

    public void process(Path path) {
        DataAnalyzer analyzer = new DataAnalyzer();
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            lines.forEach(line -> registry.parse(line).ifPresent(analyzer::accept));
            reportWriter.write(path, analyzer.summarize());
            log.info("Arquivo processado: {}", path.getFileName());
        } catch (IOException e) {
            log.error("Falha de I/O ao processar {}", path, e);
        } catch (RuntimeException e) {
            log.error("Erro inesperado ao processar {}", path, e);
        }
    }
}
