package com.danilomendes.dataanalyzer.parser;

import com.danilomendes.dataanalyzer.domain.DataRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ParserRegistry {

    private static final Logger log = LoggerFactory.getLogger(ParserRegistry.class);

    private final Map<String, LineParser<? extends DataRecord>> parsersByPrefix;

    // Novos tipos de registro entram como novos beans LineParser, sem alterar esta classe.
    public ParserRegistry(List<LineParser<? extends DataRecord>> parsers) {
        this.parsersByPrefix = parsers.stream().collect(Collectors.toUnmodifiableMap(
            LineParser::prefix,
            Function.identity(),
            (first, second) -> {
                throw new IllegalStateException("Duplicate parser for prefix " + first.prefix());
            }
        ));
    }

    public Optional<DataRecord> parse(String line) {
        if (line == null || line.isBlank()) {
            log.warn("Skipping blank line");
            return Optional.empty();
        }
        int delimiterIndex = line.indexOf(LineParser.DELIMITER);
        if (delimiterIndex < 0) {
            log.warn("Skipping line without delimiter: {}", line);
            return Optional.empty();
        }
        String prefix = line.substring(0, delimiterIndex);
        LineParser<? extends DataRecord> parser = parsersByPrefix.get(prefix);
        if (parser == null) {
            log.warn("Skipping line with unknown prefix '{}': {}", prefix, line);
            return Optional.empty();
        }
        // Optional<T> não é covariante; widening explícito para Optional<DataRecord>.
        return parser.parse(line).map(DataRecord.class::cast);
    }
}
