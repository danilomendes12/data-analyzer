package com.danilomendes.dataanalyzer.io;

import com.danilomendes.dataanalyzer.domain.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Escreve o relatório de 4 linhas em UTF-8, de forma atômica: grava num arquivo temporário no mesmo
 * diretório de saída e move com {@code ATOMIC_MOVE}. Um {@code .done.dat} truncado por crash seria lido
 * como "já processado" pela varredura da Opção B; a atomicidade protege esse mecanismo de skip.
 */
@Component
public class ReportWriter {

    private static final Logger log = LoggerFactory.getLogger(ReportWriter.class);
    private static final String NOT_AVAILABLE = "N/A";

    private final OutputPathResolver outputPathResolver;

    public ReportWriter(OutputPathResolver outputPathResolver) {
        this.outputPathResolver = outputPathResolver;
    }

    public void write(Path input, Report report) throws IOException {
        Path destination = outputPathResolver.outputPathFor(input);
        Path tmp = Files.createTempFile(destination.getParent(), "report-", ".tmp");
        try {
            Files.writeString(tmp, format(report), StandardCharsets.UTF_8);
            moveIntoPlace(tmp, destination);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private void moveIntoPlace(Path tmp, Path destination) throws IOException {
        try {
            Files.move(tmp, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            log.warn("Move atômico não suportado para {}; usando move não atômico", destination, e);
            Files.move(tmp, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String format(Report report) {
        return "Quantidade de clientes: " + report.customerCount() + '\n'
            + "Quantidade de vendedores: " + report.sellerCount() + '\n'
            + "ID da venda mais cara: " + report.mostExpensiveSaleId().map(String::valueOf).orElse(NOT_AVAILABLE) + '\n'
            + "Pior vendedor (menor volume de vendas): " + report.worstSellerName().orElse(NOT_AVAILABLE) + '\n';
    }
}
