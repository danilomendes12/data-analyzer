package com.danilomendes.dataanalyzer.io;

import com.danilomendes.dataanalyzer.config.AppProperties;
import com.danilomendes.dataanalyzer.domain.Report;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReportWriterTest {

    @TempDir
    Path inputDir;
    @TempDir
    Path outputDir;

    private ReportWriter writer;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(inputDir, outputDir);
        writer = new ReportWriter(new OutputPathResolver(properties));
    }

    @Test
    void writesOfficialExampleLineByLineToDoneFile() throws IOException {
        Report report = new Report(2, 2, Optional.of(10L), Optional.of("Paulo"));

        writer.write(inputDir.resolve("vendas.dat"), report);

        Path output = outputDir.resolve("vendas.done.dat");
        assertThat(Files.readAllLines(output, StandardCharsets.UTF_8)).containsExactly(
            "Quantidade de clientes: 2",
            "Quantidade de vendedores: 2",
            "ID da venda mais cara: 10",
            "Pior vendedor (menor volume de vendas): Paulo"
        );
    }

    @Test
    void writesNotAvailableWhenDerivedFieldsAreEmpty() throws IOException {
        Report report = new Report(0, 0, Optional.empty(), Optional.empty());

        writer.write(inputDir.resolve("empty.dat"), report);

        assertThat(Files.readAllLines(outputDir.resolve("empty.done.dat"), StandardCharsets.UTF_8)).containsExactly(
            "Quantidade de clientes: 0",
            "Quantidade de vendedores: 0",
            "ID da venda mais cara: N/A",
            "Pior vendedor (menor volume de vendas): N/A"
        );
    }

    @Test
    void leavesNoOrphanTempFileAfterSuccess() throws IOException {
        writer.write(inputDir.resolve("vendas.dat"), new Report(1, 1, Optional.of(1L), Optional.of("Ana")));

        try (Stream<Path> entries = Files.list(outputDir)) {
            List<String> names = entries.map(path -> path.getFileName().toString()).toList();
            assertThat(names).containsExactly("vendas.done.dat");
        }
    }
}
