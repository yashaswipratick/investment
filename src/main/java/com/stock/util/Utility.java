package com.stock.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.stock.dto.StockDescriptionDetails;
import com.stock.dto.StockInfoDetails;
import reactor.core.publisher.Mono;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Utility {

    public static List<StockDescriptionDetails> convertCSVToList(String csvdata) {
        if (csvdata.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            List<StockDescriptionDetails> resultList = new ArrayList<>();
            //Map<String, String> resultMap = new HashMap<>();
            CSVReader csvReader = new CSVReader(new StringReader(csvdata));
            List<String[]> lines = csvReader.readAll();

            // Skip header row if present
            if (!lines.isEmpty()) {
                lines.remove(0);
            }

            for (String[] line : lines) {
                if (line.length >= 8) { // Ensure all columns are present
                    StockDescriptionDetails stockDescription = StockDescriptionDetails.builder()
                            .symbol(line[0].trim())
                            .companyName(line[1].trim())
                            .series(line[2].trim())
                            .dateOfListing(line[3].trim())
                            .paidUpValue(Double.parseDouble(line[4].trim()))
                            .marketLot(Integer.parseInt(line[5].trim()))
                            .isInNumber(line[6].trim())
                            .faceValue(Integer.parseInt(line[7].trim()))
                            .createdDate(LocalDateTime.now()) // Example: Set current timestamp
                            .build();

                    resultList.add(stockDescription);
                }
            }
            System.out.println(resultList);
            return resultList;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public StockInfoDetails stringToDto(String jsonResponse) {
        //String jsonResponse = "{\"info\":{\"symbol\":\"RELIANCE\",\"companyName\":\"Reliance Industries Limited\",\"industry\":\"REFINERIES\",\"activeSeries\":[\"EQ\"],\"debtSeries\":[],\"isFNOSec\":true,\"isCASec\":false,\"isSLBSec\":true,\"isDebtSec\":false,\"isSuspended\":false,\"tempSuspendedSeries\":[],\"isETFSec\":false,\"isDelisted\":false,\"isin\":\"INE002A01018\",\"isMunicipalBond\":false,\"isTop10\":false,\"identifier\":\"RELIANCEEQN\"},\"metadata\":{\"series\":\"EQ\",\"symbol\":\"RELIANCE\",\"isin\":\"INE002A01018\",\"status\":\"Listed\",\"listingDate\":\"29-Nov-1995\",\"industry\":\"Refineries & Marketing\",\"lastUpdateTime\":\"26-Jun-2024 10:56:52\",\"pdSectorPe\":25.02,\"pdSymbolPe\":25.02,\"pdSectorInd\":\"NIFTY 500                                         \"},\"securityInfo\":{\"boardStatus\":\"Main\",\"tradingStatus\":\"Active\",\"tradingSegment\":\"Normal Market\",\"sessionNo\":\"-\",\"slb\":\"Yes\",\"classOfShare\":\"Equity\",\"derivatives\":\"Yes\",\"surveillance\":{\"surv\":null,\"desc\":null},\"faceValue\":10,\"issuedSize\":6765813926},\"sddDetails\":{\"SDDAuditor\":\"-\",\"SDDStatus\":\"-\"},\"priceInfo\":{\"lastPrice\":2947.7,\"change\":39.399999999999636,\"pChange\":1.3547433208403408,\"previousClose\":2908.3,\"open\":2892.1,\"close\":0,\"vwap\":2925.97,\"lowerCP\":\"2617.50\",\"upperCP\":\"3199.10\",\"pPriceBand\":\"No Band\",\"basePrice\":2908.3,\"intraDayHighLow\":{\"min\":2890.25,\"max\":2949.9,\"value\":2947.7},\"weekHighLow\":{\"min\":2220.3,\"minDate\":\"26-Oct-2023\",\"max\":3029,\"maxDate\":\"03-Jun-2024\",\"value\":2947.7},\"iNavValue\":null,\"checkINAV\":false},\"industryInfo\":{\"macro\":\"Energy\",\"sector\":\"Oil Gas & Consumable Fuels\",\"industry\":\"Petroleum Products\",\"basicIndustry\":\"Refineries & Marketing\"},\"preOpenMarket\":{\"preopen\":[{\"price\":2617.5,\"buyQty\":0,\"sellQty\":25},{\"price\":2618,\"buyQty\":0,\"sellQty\":15},{\"price\":2680,\"buyQty\":0,\"sellQty\":66},{\"price\":2762.9,\"buyQty\":0,\"sellQty\":5106},{\"price\":2892.1,\"buyQty\":0,\"sellQty\":0,\"iep\":true},{\"price\":3053.7,\"buyQty\":3578,\"sellQty\":0},{\"price\":3080,\"buyQty\":3,\"sellQty\":0},{\"price\":3100,\"buyQty\":1250,\"sellQty\":0},{\"price\":3199.1,\"buyQty\":401,\"sellQty\":0}],\"ato\":{\"buy\":6022,\"sell\":12452},\"IEP\":2892.1,\"totalTradedVolume\":119958,\"finalPrice\":2892.1,\"finalQuantity\":119958,\"lastUpdateTime\":\"26-Jun-2024 09:07:47\",\"totalBuyQuantity\":63088,\"totalSellQuantity\":131612,\"atoBuyQty\":6022,\"atoSellQty\":12452,\"Change\":-16.200000000000273,\"perChange\":-0.5570264415638095,\"prevClose\":2908.3}}"; // Replace with your actual JSON string

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        StockInfoDetails stockInfoDetails = null;
        try {
            stockInfoDetails = objectMapper.readValue(jsonResponse, StockInfoDetails.class);
            //System.out.println("Inside converter... " + stockInfoDetails);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stockInfoDetails;
    }

    public String dtoToString(StockInfoDetails details) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            return mapper.writeValueAsString(details);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
