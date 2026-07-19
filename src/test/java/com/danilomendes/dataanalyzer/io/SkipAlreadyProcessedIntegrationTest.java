package com.danilomendes.dataanalyzer.io;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mecanismo de skip da Opção B: um {@code .dat} de entrada cujo {@code .done.dat} correspondente já existe
 * na saída (de uma execução anterior) NÃO deve ser reprocessado na subida. Validamos que o conteúdo e o
 * {@code lastModified} da saída permanecem intactos após uma janela de espera — se houvesse reprocessamento,
 * o relatório real sobrescreveria o sentinela.
 *
 * <p>Como no teste da varredura inicial, tanto a entrada quanto o {@code .done.dat} são semeados ANTES do
 * boot, e {@code @DirtiesContext} impede o reaproveitamento do contexto de outra classe.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SkipAlreadyProcessedIntegrationTest {

    // Sentinela deliberadamente diferente de qualquer relatório real: se o arquivo for reprocessado, muda.
    private static final String SENTINEL = "NAO_REPROCESSAR\n";

    static Path baseDir;
    static Path outputDir;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) throws IOException {
        baseDir = Files.createTempDirectory("data-analyzer-skip");
        Path inputDir = baseDir.resolve("in");
        outputDir = baseDir.resolve("out");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);
        // Entrada presente...
        IntegrationTestFiles.copyResource("dados-teste.dat", inputDir.resolve("vendas.dat"));
        // ...e o .done.dat correspondente já na saída, com conteúdo sentinela.
        Files.writeString(outputDir.resolve("vendas.done.dat"), SENTINEL, StandardCharsets.UTF_8);
        registry.add("app.input-dir", inputDir::toString);
        registry.add("app.output-dir", outputDir::toString);
    }

    @AfterAll
    static void cleanUp() throws IOException {
        IntegrationTestFiles.deleteRecursively(baseDir);
    }

    @Test
    void doesNotReprocessFileThatAlreadyHasDoneFile() throws IOException {
        Path done = outputDir.resolve("vendas.done.dat");
        FileTime originalModifiedTime = Files.getLastModifiedTime(done);

        // Espera uma janela real; se o arquivo fosse reprocessado, o conteúdo/lastModified mudariam nela.
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(Files.readString(done, StandardCharsets.UTF_8)).isEqualTo(SENTINEL);
            assertThat(Files.getLastModifiedTime(done)).isEqualTo(originalModifiedTime);
        });
    }
}
