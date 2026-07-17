package com.danilomendes.dataanalyzer.domain;

import java.util.List;

public record Sale(
    Long id,
    List<SaleItem> items,
    String salesmanName
) implements DataRecord {}