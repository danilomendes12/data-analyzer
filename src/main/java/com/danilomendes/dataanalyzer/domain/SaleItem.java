package com.danilomendes.dataanalyzer.domain;

import java.math.BigDecimal;

public record SaleItem(
    Long id,
    Integer quantity,
    BigDecimal price
) {}