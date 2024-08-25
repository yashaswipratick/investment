package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("pre_open")
public class PreOpen {

    private String price;
    private int buyQty;
    private int sellQty;
    private boolean iep;
}
