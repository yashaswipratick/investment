package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("metadata")
public class Metadata {

    private String series;
    private String symbol;
    private String isin;
    private String status;
    private String listingDate;
    private String industry;
    private String lastUpdateTime;
    private String pdSectorPe;
    private String pdSymbolPe;
    private String pdSectorInd;
}
