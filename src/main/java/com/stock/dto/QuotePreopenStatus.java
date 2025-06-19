package com.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("quote_preopen_status")
public class QuotePreopenStatus {
    private String equityTime;
    private String preOpenTime;
    @JsonProperty("QuotePreOpenFlag")
    private Boolean quotePreOpenFlag;
}
