package com.danilomendes.dataanalyzer.io;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre a deduplicação em voo do submitter — o mecanismo central da narrativa de concorrência.
 * Sem este teste, quebrar o {@code inFlight} num refactor não faria nenhum teste falhar de forma
 * determinística (o de integração passaria por sorte, já que reprocessar dá o mesmo relatório).
 */
class FileTaskSubmitterTest {

    private FileProcessor processor;
    private StableFileDetector stableFileDetector;
    private FileTaskSubmitter submitter;

    @BeforeEach
    void setUp() {
        processor = mock(FileProcessor.class);
        stableFileDetector = mock(StableFileDetector.class);
        when(stableFileDetector.isStable(any())).thenReturn(true);
        submitter = new FileTaskSubmitter(processor, stableFileDetector);
    }

    @AfterEach
    void tearDown() {
        submitter.shutdown();
    }

    @Test
    void ignoresDuplicateSubmissionOfPathAlreadyInFlight() throws InterruptedException {
        Path path = Path.of("vendas.dat");
        Path key = path.toAbsolutePath().normalize();
        CountDownLatch processingStarted = new CountDownLatch(1);
        CountDownLatch releaseProcessing = new CountDownLatch(1);
        // Mantém a primeira tarefa "em voo" enquanto submetemos a segunda.
        doAnswer(invocation -> {
            processingStarted.countDown();
            releaseProcessing.await();
            return null;
        }).when(processor).process(key);

        submitter.submit(path);
        assertThat(processingStarted.await(2, TimeUnit.SECONDS)).isTrue();

        // Mesmo path, ainda em processamento → deve ser ignorado sem reprocessar.
        submitter.submit(path);
        releaseProcessing.countDown();

        verify(processor, times(1)).process(key);
    }

    @Test
    void doesNotProcessAnUnstableFile() {
        // Arquivo ainda em escrita (isStable == false): a tarefa desiste e nunca chama o processor.
        // Cobre o ramo de instabilidade do runTask — o path é recuperado na próxima varredura.
        when(stableFileDetector.isStable(any())).thenReturn(false);
        Path path = Path.of("em-escrita.dat");
        Path key = path.toAbsolutePath().normalize();

        submitter.submit(path);

        // O inFlight é liberado no finally mesmo sem processar: uma resubmissão volta a ser aceita.
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            submitter.submit(path);
            verify(stableFileDetector, times(2)).isStable(key);
        });
        verify(processor, never()).process(any());
    }

    @Test
    void reinterruptsAndForcesShutdownWhenInterruptedWhileDraining() throws InterruptedException {
        // Com uma tarefa ainda em execução, awaitTermination bloqueia; interrompendo a thread nesse
        // momento ele lança InterruptedException → o pool é forçado com shutdownNow e o status é
        // repropagado (nunca engolido). Sem a tarefa presa o pool encerraria na hora e o ramo não rodaria.
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch block = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            block.await();
            return null;
        }).when(processor).process(any());
        submitter.submit(Path.of("presa.dat"));
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        Thread.currentThread().interrupt();
        submitter.shutdown();

        assertThat(Thread.interrupted()).isTrue(); // consome o status para não vazar aos próximos testes
        block.countDown();
    }

    @Test
    void allowsResubmissionOfSamePathAfterItFinishes() {
        Path path = Path.of("vendas.dat");
        Path key = path.toAbsolutePath().normalize();

        submitter.submit(path);
        await().atMost(2, TimeUnit.SECONDS)
            .untilAsserted(() -> verify(processor, times(1)).process(key));

        // Encerrada a primeira tarefa, o path sai do inFlight (no finally) e pode ser resubmetido.
        // Poll até a resubmissão passar: submissões durante a janela de remoção são simplesmente
        // deduplicadas, e a primeira após a liberação dispara o segundo processamento.
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            submitter.submit(path);
            verify(processor, times(2)).process(key);
        });
    }
}
