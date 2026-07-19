package com.danilomendes.dataanalyzer.io;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Varredura de subida (Opção B): um {@code .dat} já presente no diretório de entrada, SEM o
 * {@code .done.dat} correspondente, deve ser processado quando a aplicação sobe — não só quando um
 * evento de watch chega.
 *
 * <p>Classe separada de propósito: o arquivo é criado dentro do bloco {@code @DynamicPropertySource},
 * ANTES de o contexto subir, para que a varredura inicial o encontre. {@code @DirtiesContext} garante
 * que o Spring não reaproveite o contexto de outra classe de integração — se reaproveitasse, a varredura
 * de subida não rodaria contra este diretório e o teste passaria (ou falharia) por engano.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InitialScanStartupIntegrationTest {

    static Path baseDir;
    static Path outputDir;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) throws IOException {
        baseDir = Files.createTempDirectory("data-analyzer-startup-scan");
        Path inputDir = baseDir.resolve("in");
        outputDir = baseDir.resolve("out");
        Files.createDirectories(inputDir);
        // Semeia o arquivo ANTES do boot: é isto que a varredura de subida precisa encontrar.
        try (InputStream in = InitialScanStartupIntegrationTest.class.getClassLoader()
                .getResourceAsStream("dados-teste.dat")) {
            Files.copy(in, inputDir.resolve("pre-existente.dat"));
        }
        registry.add("app.input-dir", inputDir::toString);
        registry.add("app.output-dir", outputDir::toString);
    }

    @AfterAll
    static void cleanUp() throws IOException {
        DirectoryWatchingIntegrationTest.deleteRecursively(baseDir);
    }

    @Test
    void processesFilePresentBeforeStartup() {
        Path done = outputDir.resolve("pre-existente.done.dat");
        await().atMost(Duration.ofSeconds(10)).until(() -> Files.exists(done));

        assertThat(readLines(done)).containsExactly(
            "Quantidade de clientes: 2",
            "Quantidade de vendedores: 2",
            "ID da venda mais cara: 10",
            "Pior vendedor (menor volume de vendas): Paulo");
    }

    private static java.util.List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
