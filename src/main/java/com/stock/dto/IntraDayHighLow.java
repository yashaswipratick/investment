package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("intra_day_high_low")
public class IntraDayHighLow {

    private String min;
    private String max;
    private String value;
}
