package com.danilomendes.dataanalyzer.parser;

import com.danilomendes.dataanalyzer.domain.Seller;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SellerLineParserTest {

    private final SellerLineParser parser = new SellerLineParser();

    @Test
    void exposesSellerPrefix() {
        assertThat(parser.prefix()).isEqualTo("001");
    }

    @Test
    void parsesOfficialSellerLine() {
        assertThat(parser.parse("001ç1234567891234çPedroç50000"))
            .contains(new Seller(new BigDecimal("50000"), "Pedro", "1234567891234"));
    }

    @Test
    void parsesSalaryWithDecimals() {
        assertThat(parser.parse("001ç3245678865434çPauloç40000.99"))
            .contains(new Seller(new BigDecimal("40000.99"), "Paulo", "3245678865434"));
    }

    @Test
    void returnsEmptyWhenFieldIsMissing() {
        assertThat(parser.parse("001ç1234567891234çPedro")).isEmpty();
    }

    @Test
    void returnsEmptyWhenLineHasExtraFields() {
        assertThat(parser.parse("001ç1234567891234çPedroç50000çextra")).isEmpty();
    }

    @Test
    void returnsEmptyWhenSalaryIsNotANumber() {
        assertThat(parser.parse("001ç1234567891234çPedroçabc")).isEmpty();
    }

    @Test
    void returnsEmptyWhenPrefixDoesNotMatch() {
        assertThat(parser.parse("002ç1234567891234çPedroç50000")).isEmpty();
    }
}
