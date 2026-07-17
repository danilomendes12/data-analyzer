package com.danilomendes.dataanalyzer.domain;

import java.math.BigDecimal;

public record Seller(
    BigDecimal monthlyWage,
    String name,
    String document
) implements DataRecord {}