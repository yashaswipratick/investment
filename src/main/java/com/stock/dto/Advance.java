package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("advance")
public class Advance {

    private String declines;
    private String advances;
    private String unchanged;
}
