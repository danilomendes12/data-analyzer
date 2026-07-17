package com.danilomendes.dataanalyzer.domain;

import java.util.Optional;

// Optional nos campos derivados: sem vendas não há venda mais cara, e sem vendedores 001 não há pior vendedor.
public record Report(
    long customerCount,
    long sellerCount,
    Optional<Long> mostExpensiveSaleId,
    Optional<String> worstSellerName
) {}
