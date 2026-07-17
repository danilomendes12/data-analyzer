package com.danilomendes.dataanalyzer.parser;

import com.danilomendes.dataanalyzer.domain.DataRecord;

import java.util.Optional;

public interface LineParser<T extends DataRecord> {

    // Delimitador 'ç' (U+00E7) exige que os arquivos .dat sejam lidos explicitamente como UTF-8.
    String DELIMITER = "ç";

    String prefix();

    Optional<T> parse(String line);
}
