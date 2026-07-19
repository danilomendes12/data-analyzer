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
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integração do watcher em regime: com a aplicação de pé, soltar um {@code .dat} no diretório de entrada
 * → aguardar o {@code .done.dat} correto na saída. Cobre o loop de eventos, submissão, estabilidade,
 * processamento e escrita atômica de uma vez só. Os dados vêm de recursos {@code .dat} de teste (mesmo
 * formato do enunciado), não de listas inline, para exercitar o parsing do arquivo real.
 *
 * <p>{@code @DirtiesContext(AFTER_CLASS)}: cada classe de integração precisa do seu próprio contexto, com
 * os seus próprios diretórios temporários. Marcar o contexto como sujo ao fim da classe garante que ele
 * não seja reaproveitado por outra classe (e ainda exercita o desligamento ordenado via {@code @PreDestroy}).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
        deleteRecursively(baseDir);
    }

    @Test
    void generatesDoneFileForOfficialExample() throws IOException {
        copyResourceToInput("dados-teste.dat", "vendas.dat");

        assertReportEventually("vendas.done.dat",
            "Quantidade de clientes: 2",
            "Quantidade de vendedores: 2",
            "ID da venda mais cara: 10",
            "Pior vendedor (menor volume de vendas): Paulo");
    }

    @Test
    void ignoresMalformedLinesButProcessesValidOnes() throws IOException {
        // Mesmo dataset válido do exemplo oficial, mas com linhas malformadas (prefixo desconhecido, item
        // com número inválido, linha em branco) intercaladas. O relatório deve ser idêntico ao caso limpo.
        copyResourceToInput("dados-com-linhas-invalidas.dat", "sujo.dat");

        assertReportEventually("sujo.done.dat",
            "Quantidade de clientes: 2",
            "Quantidade de vendedores: 2",
            "ID da venda mais cara: 10",
            "Pior vendedor (menor volume de vendas): Paulo");
    }

    @Test
    void processesTwoFilesDroppedConcurrently() throws IOException {
        // Dois arquivos soltos juntos, com relatórios distintos: valida a concorrência básica do pool e o
        // isolamento por arquivo (cada um tem seu próprio DataAnalyzer; não pode haver contaminação cruzada).
        copyResourceToInput("dados-teste.dat", "a.dat");
        copyResourceToInput("outro-dataset.dat", "b.dat");

        assertReportEventually("a.done.dat",
            "Quantidade de clientes: 2",
            "Quantidade de vendedores: 2",
            "ID da venda mais cara: 10",
            "Pior vendedor (menor volume de vendas): Paulo");
        assertReportEventually("b.done.dat",
            "Quantidade de clientes: 1",
            "Quantidade de vendedores: 2",
            "ID da venda mais cara: 7",
            "Pior vendedor (menor volume de vendas): Ana");
    }

    private void assertReportEventually(String doneFileName, String... expectedLines) {
        Path done = outputDir.resolve(doneFileName);
        await().atMost(Duration.ofSeconds(10)).until(() -> Files.exists(done));
        assertThat(readLines(done)).containsExactly(expectedLines);
    }

    private static void copyResourceToInput(String resource, String targetName) throws IOException {
        try (InputStream in = resourceStream(resource)) {
            Files.copy(in, inputDir.resolve(targetName));
        }
    }

    private static InputStream resourceStream(String resource) {
        InputStream in = DirectoryWatchingIntegrationTest.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Recurso de teste não encontrado no classpath: " + resource);
        }
        return in;
    }

    private static java.util.List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static void deleteRecursively(Path root) throws IOException {
        if (root == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Melhor esforço: o watcher pode ainda segurar o diretório neste ponto.
                }
            });
        }
    }
}
