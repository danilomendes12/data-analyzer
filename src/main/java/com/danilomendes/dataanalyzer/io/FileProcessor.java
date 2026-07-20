package com.danilomendes.dataanalyzer.io;

import com.danilomendes.dataanalyzer.analysis.DataAnalyzer;
import com.danilomendes.dataanalyzer.parser.ParserRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
        try (BufferedReader reader = newLenientUtf8Reader(path)) {
            reader.lines().forEach(line -> registry.parse(line).ifPresent(analyzer::accept));
            reportWriter.write(path, analyzer.summarize());
            log.info("Arquivo processado: {}", path.getFileName());
        } catch (IOException e) {
            log.error("Falha de I/O ao processar {}", path, e);
        } catch (RuntimeException e) {
            log.error("Erro inesperado ao processar {}", path, e);
        }
    }

    // UTF-8 tolerante: bytes inválidos (ex.: um .dat legado em CP1252/ISO-8859-1, onde 'ç' é o byte 0xE7)
    // viram U+FFFD em vez de estourar UncheckedIOException no meio do stream. A linha corrompida perde o
    // delimitador e cai no mesmo contrato de "linha malformada" — logada e ignorada. Sem isso, um único
    // byte legado tornaria o arquivo inteiro um "poison file", retentado a cada subida sem nunca ter
    // sucesso. O CharsetDecoder não é thread-safe: um novo por chamada.
    private static BufferedReader newLenientUtf8Reader(Path path) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
        return new BufferedReader(new InputStreamReader(Files.newInputStream(path), decoder));
    }
}
