package com.danilomendes.dataanalyzer.domain;

// Tipo selado dos registros de topo de um arquivo .dat; SaleItem fica de fora por ser aninhado em Sale.
public sealed interface DataRecord permits Seller, Customer, Sale {
}
