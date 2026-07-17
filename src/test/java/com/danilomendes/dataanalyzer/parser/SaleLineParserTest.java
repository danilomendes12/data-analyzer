package com.danilomendes.dataanalyzer.parser;

import com.danilomendes.dataanalyzer.domain.Sale;
import com.danilomendes.dataanalyzer.domain.SaleItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SaleLineParserTest {

    private final SaleLineParser parser = new SaleLineParser();

    @Test
    void exposesSalePrefix() {
        assertThat(parser.prefix()).isEqualTo("003");
    }

    @Test
    void parsesOfficialSaleLineWithMultipleItemsAndDecimalPrices() {
        assertThat(parser.parse("003ç10ç[1-10-100,2-30-2.50,3-40-3.10]çPedro"))
            .contains(new Sale(10L, List.of(
                new SaleItem(1L, 10, new BigDecimal("100")),
                new SaleItem(2L, 30, new BigDecimal("2.50")),
                new SaleItem(3L, 40, new BigDecimal("3.10"))
            ), "Pedro"));
    }

    @Test
    void parsesSaleIdWithLeadingZero() {
        assertThat(parser.parse("003ç08ç[1-34-10,2-33-1.50,3-40-0.10]çPaulo"))
            .contains(new Sale(8L, List.of(
                new SaleItem(1L, 34, new BigDecimal("10")),
                new SaleItem(2L, 33, new BigDecimal("1.50")),
                new SaleItem(3L, 40, new BigDecimal("0.10"))
            ), "Paulo"));
    }

    @Test
    void parsesSingleItem() {
        assertThat(parser.parse("003ç1ç[5-2-19.90]çAna"))
            .contains(new Sale(1L, List.of(new SaleItem(5L, 2, new BigDecimal("19.90"))), "Ana"));
    }

    @Test
    void parsesEmptyItemListAsValidSale() {
        assertThat(parser.parse("003ç10ç[]çPedro"))
            .contains(new Sale(10L, List.of(), "Pedro"));
    }

    @Test
    void returnsEmptyWhenFieldIsMissing() {
        assertThat(parser.parse("003ç10ç[1-10-100]")).isEmpty();
    }

    @Test
    void returnsEmptyWhenSaleIdIsNotANumber() {
        assertThat(parser.parse("003çabcç[1-10-100]çPedro")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "003ç10ç1-10-100]çPedro",
        "003ç10ç[1-10-100çPedro",
        "003ç10ç1-10-100çPedro"
    })
    void returnsEmptyWhenBracketsAreBroken(String line) {
        assertThat(parser.parse(line)).isEmpty();
    }

    @Test
    void returnsEmptyWhenItemIsMissingParts() {
        assertThat(parser.parse("003ç10ç[1-10]çPedro")).isEmpty();
    }

    @Test
    void returnsEmptyWhenItemHasNonNumericPart() {
        assertThat(parser.parse("003ç10ç[1-abc-100]çPedro")).isEmpty();
    }

    @Test
    void returnsEmptyWhenPrefixDoesNotMatch() {
        assertThat(parser.parse("001ç10ç[1-10-100]çPedro")).isEmpty();
    }
}
