package com.danilomendes.dataanalyzer.analysis;

import com.danilomendes.dataanalyzer.domain.Customer;
import com.danilomendes.dataanalyzer.domain.DataRecord;
import com.danilomendes.dataanalyzer.domain.Report;
import com.danilomendes.dataanalyzer.domain.Sale;
import com.danilomendes.dataanalyzer.domain.SaleItem;
import com.danilomendes.dataanalyzer.domain.Seller;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataAnalyzerTest {

    private final DataAnalyzer analyzer = new DataAnalyzer();

    private static Seller seller(String name, String document) {
        return new Seller(new BigDecimal("1000"), name, document);
    }

    private static Customer customer(String document) {
        return new Customer("Any Customer", document, "Any Segment");
    }

    private static Sale sale(long id, String salesmanName, SaleItem... items) {
        return new Sale(id, List.of(items), salesmanName);
    }

    private void feed(DataRecord... records) {
        for (DataRecord record : records) {
            analyzer.accept(record);
        }
    }

    @Test
    void countsDistinctSellerCpfsAndCustomerCnpjs() {
        feed(
            seller("Pedro", "111"),
            seller("Pedro again same cpf", "111"),
            seller("Paulo", "222"),
            customer("aaa"),
            customer("aaa"),
            customer("bbb")
        );

        Report report = analyzer.summarize();

        assertThat(report.sellerCount()).isEqualTo(2);
        assertThat(report.customerCount()).isEqualTo(2);
    }

    @Test
    void picksMostExpensiveSaleAndBreaksTieBySmallestId() {
        // Duas vendas de mesmo valor (200); desempate pelo menor ID (7 < 9).
        feed(
            sale(9, "Pedro", new SaleItem(1L, 2, new BigDecimal("100"))),
            sale(7, "Paulo", new SaleItem(1L, 1, new BigDecimal("200"))),
            sale(3, "Ana", new SaleItem(1L, 1, new BigDecimal("50")))
        );

        Report report = analyzer.summarize();

        assertThat(report.mostExpensiveSaleId()).contains(7L);
    }

    @Test
    void worstSellerIsRegisteredSellerWithNoSalesAtVolumeZero() {
        feed(
            seller("Pedro", "111"),
            seller("Paulo", "222"),
            sale(1, "Pedro", new SaleItem(1L, 1, new BigDecimal("500")))
        );

        Report report = analyzer.summarize();

        // Paulo está cadastrado mas não vendeu nada → volume 0 → pior vendedor.
        assertThat(report.worstSellerName()).contains("Paulo");
    }

    @Test
    void worstSellerTieIsBrokenByAlphabeticalName() {
        // Ana e Bruno cadastrados, ambos sem vendas → empate em volume 0 → menor nome (Ana).
        feed(
            seller("Bruno", "111"),
            seller("Ana", "222")
        );

        Report report = analyzer.summarize();

        assertThat(report.worstSellerName()).contains("Ana");
    }

    @Test
    void saleForUnregisteredSellerDoesNotBecomeWorstSellerButStillCompetesForMostExpensive() {
        feed(
            seller("Pedro", "111"),
            sale(1, "Pedro", new SaleItem(1L, 1, new BigDecimal("100"))),
            // "Ghost" não tem 001; venda enorme, mas não concorre a pior vendedor.
            sale(2, "Ghost", new SaleItem(1L, 1, new BigDecimal("9999")))
        );

        Report report = analyzer.summarize();

        assertThat(report.worstSellerName()).contains("Pedro");
        assertThat(report.mostExpensiveSaleId()).contains(2L);
    }

    @Test
    void emptyDatasetYieldsZeroCountsAndEmptyOptionals() {
        Report report = analyzer.summarize();

        assertThat(report.sellerCount()).isZero();
        assertThat(report.customerCount()).isZero();
        assertThat(report.mostExpensiveSaleId()).isEmpty();
        assertThat(report.worstSellerName()).isEmpty();
    }

    @Test
    void sumsItemValuesWithBigDecimalPrecision() {
        // 3 × 0.10 = 0.30 exato; ponto flutuante daria 0.30000000000000004.
        feed(
            seller("Pedro", "111"),
            sale(1, "Pedro",
                new SaleItem(1L, 3, new BigDecimal("0.10")),
                new SaleItem(2L, 1, new BigDecimal("0.20")))
        );

        Report report = analyzer.summarize();

        assertThat(report.mostExpensiveSaleId()).contains(1L);
        assertThat(report.worstSellerName()).contains("Pedro");
    }

    @Test
    void resultIsIndependentOfRecordArrivalOrder() {
        // Venda 003 do Pedro chega ANTES do seu 001, e o 001 do Paulo chega por último.
        feed(
            sale(1, "Pedro", new SaleItem(1L, 1, new BigDecimal("500"))),
            seller("Pedro", "111"),
            seller("Paulo", "222")
        );

        Report report = analyzer.summarize();

        assertThat(report.sellerCount()).isEqualTo(2);
        assertThat(report.worstSellerName()).contains("Paulo");
        assertThat(report.mostExpensiveSaleId()).contains(1L);
    }

    @Test
    void officialExampleAnchor() {
        feed(
            new Seller(new BigDecimal("50000"), "Pedro", "1234567891234"),
            new Seller(new BigDecimal("40000.99"), "Paulo", "3245678865434"),
            new Customer("Jose da Silva", "2345675434544345", "Rural"),
            new Customer("Eduardo Pereira", "2345675433444345", "Rural"),
            sale(10, "Pedro",
                new SaleItem(1L, 10, new BigDecimal("100")),
                new SaleItem(2L, 30, new BigDecimal("2.50")),
                new SaleItem(3L, 40, new BigDecimal("3.10"))),
            sale(8, "Paulo",
                new SaleItem(1L, 34, new BigDecimal("10")),
                new SaleItem(2L, 33, new BigDecimal("1.50")),
                new SaleItem(3L, 40, new BigDecimal("0.10")))
        );

        Report report = analyzer.summarize();

        // Pedro = 1199.00, Paulo = 393.50 → mais cara ID 10, pior vendedor Paulo.
        assertThat(report.customerCount()).isEqualTo(2);
        assertThat(report.sellerCount()).isEqualTo(2);
        assertThat(report.mostExpensiveSaleId()).contains(10L);
        assertThat(report.worstSellerName()).contains("Paulo");
    }
}
