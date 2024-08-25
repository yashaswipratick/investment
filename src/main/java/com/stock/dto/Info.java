package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("info")
public class Info {
        private String symbol;
        private String companyName;
        private String industry;
        private List<String> activeSeries;
        private List<String> debtSeries;
        private boolean isFNOSec;
        private boolean isCASec;
        private boolean isSLBSec;
        private boolean isDebtSec;
        private boolean isSuspended;
        private List<String> tempSuspendedSeries;
        private boolean isETFSec;
        private boolean isDelisted;
        private String isin;
        private boolean isMunicipalBond;
        private boolean isTop10;
        private String identifier;

        // Getters and Setters
}
