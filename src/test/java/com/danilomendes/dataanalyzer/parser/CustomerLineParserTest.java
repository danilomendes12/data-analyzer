package com.danilomendes.dataanalyzer.parser;

import com.danilomendes.dataanalyzer.domain.Customer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerLineParserTest {

    private final CustomerLineParser parser = new CustomerLineParser();

    @Test
    void exposesCustomerPrefix() {
        assertThat(parser.prefix()).isEqualTo("002");
    }

    @Test
    void parsesOfficialCustomerLine() {
        assertThat(parser.parse("002ç2345675434544345çJose da SilvaçRural"))
            .contains(new Customer("Jose da Silva", "2345675434544345", "Rural"));
    }

    @Test
    void parsesSecondOfficialCustomerLine() {
        assertThat(parser.parse("002ç2345675433444345çEduardo PereiraçRural"))
            .contains(new Customer("Eduardo Pereira", "2345675433444345", "Rural"));
    }

    @Test
    void returnsEmptyWhenFieldIsMissing() {
        assertThat(parser.parse("002ç2345675434544345çJose da Silva")).isEmpty();
    }

    @Test
    void returnsEmptyWhenLineHasExtraFields() {
        assertThat(parser.parse("002ç2345675434544345çJose da SilvaçRuralçextra")).isEmpty();
    }

    @Test
    void returnsEmptyWhenPrefixDoesNotMatch() {
        assertThat(parser.parse("001ç2345675434544345çJose da SilvaçRural")).isEmpty();
    }
}
