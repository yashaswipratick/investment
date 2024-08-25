package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("week_high_low")
public class WeekHighLow {

    private String min;
    private String minDate;
    private String max;
    private String maxDate;
    private String value;
}
