package com.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("meta")
public class Meta {
    private String symbol;
    private String companyName;
    private String industry;
    private List<String> activeSeries;
    private List<String> debtSeries;
    private Boolean isFNOSec;
    private Boolean isCASec;
    private Boolean isSLBSec;
    private Boolean isDebtSec;
    private Boolean isSuspended;
    private List<String> tempSuspendedSeries;
    private Boolean isETFSec;
    private Boolean isDelisted;
    private String isin;
    @JsonProperty("slb_isin")
    private String slbIsin;
    private String listingDate;
    private Boolean isMunicipalBond;
    private QuotePreopenStatus quotepreopenstatus;
}
