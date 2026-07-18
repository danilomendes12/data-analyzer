package com.danilomendes.dataanalyzer.io;

import com.danilomendes.dataanalyzer.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OutputPathResolverTest {

    private final OutputPathResolver resolver =
        new OutputPathResolver(new AppProperties(Path.of("/data/in"), Path.of("/data/out")));

    @Test
    void replacesDatExtensionWithDoneDatInOutputDir() {
        assertThat(resolver.outputPathFor(Path.of("/data/in/vendas.dat")))
            .isEqualTo(Path.of("/data/out/vendas.done.dat"));
    }

    @Test
    void appendsSuffixWhenInputHasNoDatExtension() {
        assertThat(resolver.outputPathFor(Path.of("/data/in/vendas")))
            .isEqualTo(Path.of("/data/out/vendas.done.dat"));
    }
}
