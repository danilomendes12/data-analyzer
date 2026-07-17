package com.danilomendes.dataanalyzer.domain;

public record Customer(
    String name,
    String document,
    String businessSegment
) {}