package com.stock.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.stock.dto.StockData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NiftyData {

    private String name;
    private Advance advance;
    private String timestamp;
    private List<StockData> data;
}
