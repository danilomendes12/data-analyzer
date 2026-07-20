package com.danilomendes.dataanalyzer.io;

import com.danilomendes.dataanalyzer.domain.Report;
import com.danilomendes.dataanalyzer.parser.CustomerLineParser;
import com.danilomendes.dataanalyzer.parser.ParserRegistry;
import com.danilomendes.dataanalyzer.parser.SaleLineParser;
import com.danilomendes.dataanalyzer.parser.SellerLineParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FileProcessorTest {

    @TempDir
    Path inputDir;

    // Registry + parsers reais (puros e rápidos); mock só na borda de I/O (writer).
    private final ParserRegistry registry =
        new ParserRegistry(List.of(new SellerLineParser(), new CustomerLineParser(), new SaleLineParser()));
    private ReportWriter reportWriter;
    private FileProcessor processor;

    @BeforeEach
    void setUp() {
        reportWriter = mock(ReportWriter.class);
        processor = new FileProcessor(registry, reportWriter);
    }

    private Path writeDat(String name, String... lines) throws IOException {
        Path file = inputDir.resolve(name);
        Files.write(file, List.of(lines), StandardCharsets.UTF_8);
        return file;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    @Test
    void aggregatesValidLinesAndSkipsTheInvalidOneInTheMiddle() throws IOException {
        Path file = writeDat("vendas.dat",
            "001ç111çPedroç1000",
            "this line has no delimiter and must be ignored",
            "002ç222çJoseçRural",
            "003ç10ç[1-1-100]çPedro");

        processor.process(file);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportWriter).write(eq(file), captor.capture());
        Report report = captor.getValue();
        assertThat(report.sellerCount()).isEqualTo(1);
        assertThat(report.customerCount()).isEqualTo(1);
        assertThat(report.mostExpensiveSaleId()).contains(10L);
        assertThat(report.worstSellerName()).contains("Pedro");
    }

    @Test
    void doesNotPropagateWhenWriterFails() throws IOException {
        doThrow(new IOException("disco cheio")).when(reportWriter).write(any(), any());
        Path file = writeDat("vendas.dat", "001ç111çPedroç1000");

        assertThatCode(() -> processor.process(file)).doesNotThrowAnyException();
    }

    @Test
    void processesValidLinesAndSkipsALegacyEncodedLineWithoutPoisoning() throws IOException {
        // Um .dat legado (CP1252/ISO-8859-1) codifica 'ç' como o byte 0xE7, inválido em UTF-8. Antes, isso
        // estourava UncheckedIOException no meio do stream e o arquivo virava "poison file" — nunca gerava
        // .done.dat e era retentado a cada subida. Agora o byte inválido vira U+FFFD, a linha perde o
        // delimitador e é descartada como malformada; as linhas UTF-8 válidas seguem processadas.
        byte[] validUtf8 = "001ç111çPedroç1000\n".getBytes(StandardCharsets.UTF_8);
        byte[] legacyCp1252 = "002ç222çJoseçRural\n".getBytes(Charset.forName("windows-1252"));
        Path file = inputDir.resolve("legado.dat");
        Files.write(file, concat(validUtf8, legacyCp1252));

        assertThatCode(() -> processor.process(file)).doesNotThrowAnyException();

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportWriter).write(eq(file), captor.capture()); // .done.dat é escrito: não é poison file
        Report report = captor.getValue();
        assertThat(report.sellerCount()).isEqualTo(1);   // linha UTF-8 válida processada
        assertThat(report.customerCount()).isEqualTo(0); // linha legada descartada como malformada
    }

    @Test
    void doesNotPropagateWhenWriterThrowsRuntimeException() throws IOException {
        // Ramo catch(RuntimeException): qualquer falha inesperada (não-I/O) é contida, nunca derruba a
        // thread do pool. Cobre também a UncheckedIOException que o Files.lines pode lançar tardiamente.
        doThrow(new RuntimeException("falha inesperada")).when(reportWriter).write(any(), any());
        Path file = writeDat("vendas.dat", "001ç111çPedroç1000");

        assertThatCode(() -> processor.process(file)).doesNotThrowAnyException();
    }
}
