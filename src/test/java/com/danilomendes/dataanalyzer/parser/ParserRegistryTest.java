package com.danilomendes.dataanalyzer.parser;

import com.danilomendes.dataanalyzer.domain.Customer;
import com.danilomendes.dataanalyzer.domain.DataRecord;
import com.danilomendes.dataanalyzer.domain.Sale;
import com.danilomendes.dataanalyzer.domain.SaleItem;
import com.danilomendes.dataanalyzer.domain.Seller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ParserRegistryTest {

    private final ParserRegistry registry = new ParserRegistry(List.of(
        new SellerLineParser(), new CustomerLineParser(), new SaleLineParser()
    ));

    @ParameterizedTest
    @MethodSource("officialLines")
    void parsesEveryOfficialLineToItsRecord(String line, DataRecord expected) {
        assertThat(registry.parse(line)).contains(expected);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> officialLines() {
        return Stream.of(
            arguments("001ç1234567891234çPedroç50000",
                new Seller(new BigDecimal("50000"), "Pedro", "1234567891234")),
            arguments("001ç3245678865434çPauloç40000.99",
                new Seller(new BigDecimal("40000.99"), "Paulo", "3245678865434")),
            arguments("002ç2345675434544345çJose da SilvaçRural",
                new Customer("Jose da Silva", "2345675434544345", "Rural")),
            arguments("002ç2345675433444345çEduardo PereiraçRural",
                new Customer("Eduardo Pereira", "2345675433444345", "Rural")),
            arguments("003ç10ç[1-10-100,2-30-2.50,3-40-3.10]çPedro",
                new Sale(10L, List.of(
                    new SaleItem(1L, 10, new BigDecimal("100")),
                    new SaleItem(2L, 30, new BigDecimal("2.50")),
                    new SaleItem(3L, 40, new BigDecimal("3.10"))
                ), "Pedro")),
            arguments("003ç08ç[1-34-10,2-33-1.50,3-40-0.10]çPaulo",
                new Sale(8L, List.of(
                    new SaleItem(1L, 34, new BigDecimal("10")),
                    new SaleItem(2L, 33, new BigDecimal("1.50")),
                    new SaleItem(3L, 40, new BigDecimal("0.10"))
                ), "Paulo"))
        );
    }

    @Test
    void returnsEmptyForUnknownPrefix() {
        assertThat(registry.parse("004çalgoçnovo")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void returnsEmptyForBlankLine(String line) {
        assertThat(registry.parse(line)).isEmpty();
    }

    @Test
    void returnsEmptyForNullLine() {
        assertThat(registry.parse(null)).isEmpty();
    }

    @Test
    void returnsEmptyForLineWithoutDelimiter() {
        assertThat(registry.parse("001")).isEmpty();
    }

    @Test
    void rejectsParsersWithDuplicatePrefix() {
        assertThatIllegalStateException().isThrownBy(
            () -> new ParserRegistry(List.of(new SellerLineParser(), new SellerLineParser()))
        );
    }
}
