package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@UserDefinedType("stock_info_dto")
public class StockInfoDTO {


        private Info info;
        private Metadata metadata;
        private SecurityInfo securityInfo;
        private SddDetails sddDetails;
        private PriceInfo priceInfo;
        private IndustryInfo industryInfo;
        private PreOpenMarket preOpenMarket;
}
