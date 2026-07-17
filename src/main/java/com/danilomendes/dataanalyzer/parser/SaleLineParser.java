package com.danilomendes.dataanalyzer.parser;

import com.danilomendes.dataanalyzer.domain.Sale;
import com.danilomendes.dataanalyzer.domain.SaleItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class SaleLineParser implements LineParser<Sale> {

    private static final Logger log = LoggerFactory.getLogger(SaleLineParser.class);
    private static final int FIELD_COUNT = 4;
    private static final int ITEM_PART_COUNT = 3;

    @Override
    public String prefix() {
        return "003";
    }

    @Override
    public Optional<Sale> parse(String line) {
        String[] fields = line.split(DELIMITER, -1);
        if (fields.length != FIELD_COUNT || !prefix().equals(fields[0])) {
            log.warn("Skipping malformed sale line: {}", line);
            return Optional.empty();
        }
        try {
            long id = Long.parseLong(fields[1]);
            return parseItems(fields[2], line).map(items -> new Sale(id, items, fields[3]));
        } catch (NumberFormatException e) {
            log.warn("Skipping sale line with invalid number: {}", line);
            return Optional.empty();
        }
    }

    // Itens no formato [ID-Qtd-Preço,...]; qualquer item inválido descarta a venda inteira.
    private Optional<List<SaleItem>> parseItems(String rawItems, String line) {
        if (rawItems.length() < 2 || !rawItems.startsWith("[") || !rawItems.endsWith("]")) {
            log.warn("Skipping sale line with broken item brackets: {}", line);
            return Optional.empty();
        }
        String body = rawItems.substring(1, rawItems.length() - 1);
        if (body.isEmpty()) {
            return Optional.of(List.of());
        }
        List<SaleItem> items = new ArrayList<>();
        for (String rawItem : body.split(",", -1)) {
            String[] parts = rawItem.split("-", -1);
            if (parts.length != ITEM_PART_COUNT) {
                log.warn("Skipping sale line with malformed item '{}': {}", rawItem, line);
                return Optional.empty();
            }
            items.add(new SaleItem(
                Long.parseLong(parts[0]),
                Integer.parseInt(parts[1]),
                new BigDecimal(parts[2])
            ));
        }
        return Optional.of(List.copyOf(items));
    }
}
