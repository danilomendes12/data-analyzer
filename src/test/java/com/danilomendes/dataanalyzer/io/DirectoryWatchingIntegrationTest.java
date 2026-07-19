package com.danilomendes.dataanalyzer.io;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

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
        IntegrationTestFiles.deleteRecursively(baseDir);
    }

    @Test
    void generatesDoneFileForOfficialExample() {
        IntegrationTestFiles.copyResource("dados-teste.dat", inputDir.resolve("vendas.dat"));

        assertReportEventually("vendas.done.dat",
            "Quantidade de clientes: 2",
            "Quantidade de vendedores: 2",
            "ID da venda mais cara: 10",
            "Pior vendedor (menor volume de vendas): Paulo");
    }

    @Test
    void ignoresMalformedLinesButProcessesValidOnes() {
        // Mesmo dataset válido do exemplo oficial, mas com linhas malformadas (prefixo desconhecido, item
        // com número inválido, linha em branco) intercaladas. O relatório deve ser idêntico ao caso limpo.
        IntegrationTestFiles.copyResource("dados-com-linhas-invalidas.dat", inputDir.resolve("sujo.dat"));

        assertReportEventually("sujo.done.dat",
            "Quantidade de clientes: 2",
            "Quantidade de vendedores: 2",
            "ID da venda mais cara: 10",
            "Pior vendedor (menor volume de vendas): Paulo");
    }

    @Test
    void processesTwoFilesDroppedConcurrently() {
        // Dois arquivos soltos juntos, com relatórios distintos: valida a concorrência básica do pool e o
        // isolamento por arquivo (cada um tem seu próprio DataAnalyzer; não pode haver contaminação cruzada).
        IntegrationTestFiles.copyResource("dados-teste.dat", inputDir.resolve("a.dat"));
        IntegrationTestFiles.copyResource("outro-dataset.dat", inputDir.resolve("b.dat"));

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
        assertThat(IntegrationTestFiles.readLines(done)).containsExactly(expectedLines);
    }
}
