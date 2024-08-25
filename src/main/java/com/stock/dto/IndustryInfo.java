package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("industry_info")
public class IndustryInfo {

    private String macro;
    private String sector;
    private String industry;
    private String basicIndustry;
}
