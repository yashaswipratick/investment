package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("security_info")
public class SecurityInfo {

    private String boardStatus;
    private String tradingStatus;
    private String tradingSegment;
    private String sessionNo;
    private String slb;
    private String classOfShare;
    private String derivatives;
    private Surveillance surveillance;
    private int faceValue;
    private long issuedSize;
}
