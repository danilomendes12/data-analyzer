package com.danilomendes.dataanalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Diretórios de entrada e saída, ligados via constructor binding a {@code app.input-dir} e
 * {@code app.output-dir}. A criação idempotente desses diretórios acontece na subida (DirectoryWatcher).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Path inputDir, Path outputDir) {
}
