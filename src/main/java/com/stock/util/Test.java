package com.stock.util;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Test {

    public static void main(String[] args) throws IOException {
        /*String jsonResponse = "{\"info\":{\"symbol\":\"RELIANCE\",\"companyName\":\"Reliance Industries Limited\",\"industry\":\"REFINERIES\",\"activeSeries\":[\"EQ\"],\"debtSeries\":[],\"isFNOSec\":true,\"isCASec\":false,\"isSLBSec\":true,\"isDebtSec\":false,\"isSuspended\":false,\"tempSuspendedSeries\":[],\"isETFSec\":false,\"isDelisted\":false,\"isin\":\"INE002A01018\",\"isMunicipalBond\":false,\"isTop10\":false,\"identifier\":\"RELIANCEEQN\"},\"metadata\":{\"series\":\"EQ\",\"symbol\":\"RELIANCE\",\"isin\":\"INE002A01018\",\"status\":\"Listed\",\"listingDate\":\"29-Nov-1995\",\"industry\":\"Refineries & Marketing\",\"lastUpdateTime\":\"26-Jun-2024 10:56:52\",\"pdSectorPe\":25.02,\"pdSymbolPe\":25.02,\"pdSectorInd\":\"NIFTY 500                                         \"},\"securityInfo\":{\"boardStatus\":\"Main\",\"tradingStatus\":\"Active\",\"tradingSegment\":\"Normal Market\",\"sessionNo\":\"-\",\"slb\":\"Yes\",\"classOfShare\":\"Equity\",\"derivatives\":\"Yes\",\"surveillance\":{\"surv\":null,\"desc\":null},\"faceValue\":10,\"issuedSize\":6765813926},\"sddDetails\":{\"SDDAuditor\":\"-\",\"SDDStatus\":\"-\"},\"priceInfo\":{\"lastPrice\":2947.7,\"change\":39.399999999999636,\"pChange\":1.3547433208403408,\"previousClose\":2908.3,\"open\":2892.1,\"close\":0,\"vwap\":2925.97,\"lowerCP\":\"2617.50\",\"upperCP\":\"3199.10\",\"pPriceBand\":\"No Band\",\"basePrice\":2908.3,\"intraDayHighLow\":{\"min\":2890.25,\"max\":2949.9,\"value\":2947.7},\"weekHighLow\":{\"min\":2220.3,\"minDate\":\"26-Oct-2023\",\"max\":3029,\"maxDate\":\"03-Jun-2024\",\"value\":2947.7},\"iNavValue\":null,\"checkINAV\":false},\"industryInfo\":{\"macro\":\"Energy\",\"sector\":\"Oil Gas & Consumable Fuels\",\"industry\":\"Petroleum Products\",\"basicIndustry\":\"Refineries & Marketing\"},\"preOpenMarket\":{\"preopen\":[{\"price\":2617.5,\"buyQty\":0,\"sellQty\":25},{\"price\":2618,\"buyQty\":0,\"sellQty\":15},{\"price\":2680,\"buyQty\":0,\"sellQty\":66},{\"price\":2762.9,\"buyQty\":0,\"sellQty\":5106},{\"price\":2892.1,\"buyQty\":0,\"sellQty\":0,\"iep\":true},{\"price\":3053.7,\"buyQty\":3578,\"sellQty\":0},{\"price\":3080,\"buyQty\":3,\"sellQty\":0},{\"price\":3100,\"buyQty\":1250,\"sellQty\":0},{\"price\":3199.1,\"buyQty\":401,\"sellQty\":0}],\"ato\":{\"buy\":6022,\"sell\":12452},\"IEP\":2892.1,\"totalTradedVolume\":119958,\"finalPrice\":2892.1,\"finalQuantity\":119958,\"lastUpdateTime\":\"26-Jun-2024 09:07:47\",\"totalBuyQuantity\":63088,\"totalSellQuantity\":131612,\"atoBuyQty\":6022,\"atoSellQty\":12452,\"Change\":-16.200000000000273,\"perChange\":-0.5570264415638095,\"prevClose\":2908.3}}"; // Replace with your actual JSON string

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            StockInfoDTO stockInfo = objectMapper.readValue(jsonResponse, StockInfoDTO.class);
            System.out.println(stockInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }*/
                File input = new File("/Users/yashaswipratick/Documents/zerodha/23-07-2024/positions.csv");
                File output = new File("/Users/yashaswipratick/Documents/zerodha/23-07-2024/data.json");

                List<Map<?, ?>> data = readObjectsFromCsv(input);
                writeAsJson(data, output);
    }
    public static List<Map<?, ?>> readObjectsFromCsv(File file) throws IOException {
        CsvSchema bootstrap = CsvSchema.emptySchema().withHeader();
        CsvMapper csvMapper = new CsvMapper();
        try (MappingIterator<Map<?, ?>> mappingIterator = csvMapper.readerFor(Map.class).with(bootstrap).readValues(file)) {
            return mappingIterator.readAll();
        }
    }

    public static void writeAsJson(List<Map<?, ?>> data, File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String s = mapper.writeValueAsString(data);
        System.out.println(s);
        mapper.writeValue(file, data);
    }
}
