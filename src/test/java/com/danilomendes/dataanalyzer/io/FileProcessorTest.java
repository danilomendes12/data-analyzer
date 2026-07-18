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
}
