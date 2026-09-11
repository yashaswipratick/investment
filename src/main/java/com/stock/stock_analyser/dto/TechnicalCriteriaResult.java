package com.stock.stock_analyser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Deterministic evaluation of Marcus's locked technical gate. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicalCriteriaResult {
    private String overallStatus; // PASS | FAIL | UNAVAILABLE
    private String priceTrendStatus; // PASS | FAIL | UNAVAILABLE
    private String rsiStatus;
    private String macdStatus;
    private String breakoutVolumeStatus;
    private String breakoutStatus;
    private String adxStatus;
    private String summary;
}
