package com.danilomendes.dataanalyzer.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Helpers compartilhados pelas classes de integração {@code @SpringBootTest}: copiar um recurso {@code .dat}
 * para um diretório, ler as linhas de um relatório em UTF-8 e apagar recursivamente um diretório temporário.
 * Centraliza o que antes estava duplicado — e chamado de forma cruzada entre classes de teste — num único ponto.
 */
final class IntegrationTestFiles {

    private IntegrationTestFiles() {
    }

    // Copia um recurso do classpath para o caminho de destino (ex.: semear um .dat no diretório de entrada).
    static void copyResource(String resource, Path target) {
        try (InputStream in = resourceStream(resource)) {
            Files.copy(in, target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void deleteRecursively(Path root) throws IOException {
        if (root == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Melhor esforço: o watcher pode ainda segurar o diretório neste ponto.
                }
            });
        }
    }

    private static InputStream resourceStream(String resource) {
        InputStream in = IntegrationTestFiles.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Recurso de teste não encontrado no classpath: " + resource);
        }
        return in;
    }
}
