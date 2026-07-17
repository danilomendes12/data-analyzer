package com.danilomendes.dataanalyzer.parser;

import java.util.Optional;

public interface LineParser<T> {

    // Delimitador 'ç' (U+00E7) exige que os arquivos .dat sejam lidos explicitamente como UTF-8.
    String DELIMITER = "ç";

    String prefix();

    Optional<T> parse(String line);
}
