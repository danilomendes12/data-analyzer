package com.danilomendes.dataanalyzer.io;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Teste de integração fino (antecipado da Fase 4) que exercita o caminho completo do watcher: soltar um
 * .dat no diretório de entrada com a aplicação de pé → aguardar o .done.dat correto na saída. Cobre o
 * loop de eventos, submissão, estabilidade, processamento e escrita atômica de uma vez só.
 */
@SpringBootTest
class DirectoryWatchingIntegrationTest {

    static Path baseDir;
    static Path inputDir;
    static Path outputDir;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) throws IOException {
        baseDir = Files.createTempDirectory("data-analyzer-it");
        inputDir = baseDir.resolve("in");
        outputDir = baseDir.resolve("out");
        registry.add("app.input-dir", inputDir::toString);
        registry.add("app.output-dir", outputDir::toString);
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (baseDir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(baseDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Melhor esforço: o watcher (daemon) ainda pode segurar o diretório neste ponto.
                }
            });
        }
    }

    @Test
    void generatesDoneFileWhenDatFileAppears() throws IOException {
        Files.write(inputDir.resolve("vendas.dat"), List.of(
            "001ç1234567891234çPedroç50000",
            "001ç3245678865434çPauloç40000.99",
            "002ç2345675434544345çJose da SilvaçRural",
            "002ç2345675433444345çEduardo PereiraçRural",
            "003ç10ç[1-10-100,2-30-2.50,3-40-3.10]çPedro",
            "003ç08ç[1-34-10,2-33-1.50,3-40-0.10]çPaulo"
        ), StandardCharsets.UTF_8);

        Path done = outputDir.resolve("vendas.done.dat");
        await().atMost(Duration.ofSeconds(10)).until(() -> Files.exists(done));

        assertThat(Files.readAllLines(done, StandardCharsets.UTF_8)).containsExactly(
            "Quantidade de clientes: 2",
            "Quantidade de vendedores: 2",
            "ID da venda mais cara: 10",
            "Pior vendedor (menor volume de vendas): Paulo"
        );
    }
}
