package com.danilomendes.dataanalyzer.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StableFileDetectorTest {

    @TempDir
    Path dir;

    private final StableFileDetector detector = new StableFileDetector();

    @Test
    void reportsStableForAFullyWrittenFile() throws IOException {
        Path file = dir.resolve("vendas.dat");
        Files.writeString(file, "conteúdo completo", StandardCharsets.UTF_8);

        assertThat(detector.isStable(file)).isTrue();
    }

    @Test
    void reportsUnstableForAMissingFile() {
        assertThat(detector.isStable(dir.resolve("ausente.dat"))).isFalse();
    }

    @Test
    void reportsUnstableWhenInterruptedDuringPolling() throws IOException {
        Path file = dir.resolve("vendas.dat");
        Files.writeString(file, "conteúdo", StandardCharsets.UTF_8);
        // Interromper a thread antes da primeira espera: a 1ª leitura (tamanho != -1) exige mais uma
        // amostra, e o Thread.sleep entre amostras lança InterruptedException imediatamente. O detector
        // preserva o status de interrupção e desiste (retorna false), sem processar arquivo pela metade.
        Thread.currentThread().interrupt();

        assertThat(detector.isStable(file)).isFalse();
        // O status foi repropagado pelo sleep(); consome-o aqui para não vazar aos próximos testes.
        assertThat(Thread.interrupted()).isTrue();
    }
}
