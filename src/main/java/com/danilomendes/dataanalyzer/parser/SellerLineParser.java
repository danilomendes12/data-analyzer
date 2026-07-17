package com.danilomendes.dataanalyzer.parser;

import com.danilomendes.dataanalyzer.domain.Seller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class SellerLineParser implements LineParser<Seller> {

    private static final Logger log = LoggerFactory.getLogger(SellerLineParser.class);
    private static final int FIELD_COUNT = 4;

    @Override
    public String prefix() {
        return "001";
    }

    @Override
    public Optional<Seller> parse(String line) {
        String[] fields = line.split(DELIMITER, -1);
        if (fields.length != FIELD_COUNT || !prefix().equals(fields[0])) {
            log.warn("Skipping malformed seller line: {}", line);
            return Optional.empty();
        }
        try {
            return Optional.of(new Seller(new BigDecimal(fields[3]), fields[2], fields[1]));
        } catch (NumberFormatException e) {
            log.warn("Skipping seller line with invalid salary: {}", line);
            return Optional.empty();
        }
    }
}
