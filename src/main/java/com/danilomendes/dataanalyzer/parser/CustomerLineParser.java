package com.danilomendes.dataanalyzer.parser;

import com.danilomendes.dataanalyzer.domain.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CustomerLineParser implements LineParser<Customer> {

    private static final Logger log = LoggerFactory.getLogger(CustomerLineParser.class);
    private static final int FIELD_COUNT = 4;

    @Override
    public String prefix() {
        return "002";
    }

    @Override
    public Optional<Customer> parse(String line) {
        String[] fields = line.split(DELIMITER, -1);
        if (fields.length != FIELD_COUNT || !prefix().equals(fields[0])) {
            log.warn("Skipping malformed customer line: {}", line);
            return Optional.empty();
        }
        return Optional.of(new Customer(fields[2], fields[1], fields[3]));
    }
}
