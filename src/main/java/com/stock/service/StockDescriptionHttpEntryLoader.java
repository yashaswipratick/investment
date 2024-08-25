package com.stock.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dto.StockDescriptionDetails;
import com.stock.dto.StockInfoDTO;
import com.stock.dto.StockInfoDetails;
import com.stock.util.Utility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static com.stock.util.Utility.convertCSVToList;

@Slf4j
@Service
public class StockDescriptionHttpEntryLoader {

    private static final String BASE_URL = "https://www.nseindia.com/";
    private static final String URL = "https://archives.nseindia.com/content/equities/EQUITY_L.csv";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.149 Safari/537.36";
    private static final String ACCEPT_LANGUAGE = "en,gu;q=0.9,hi;q=0.8";
    private static final String ACCEPT_ENCODING = "gzip, deflate, br";

    public Flux<StockDescriptionDetails> getStockDetails() {
        WebClient client = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE)
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, ACCEPT_ENCODING)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(true)))
                .build();

        // Establish session by making a GET request to the base URL
        return client.get()
                .retrieve()
                .toBodilessEntity()
                .flatMapMany(entity -> fetchApiData(client, URL))
                .doOnError(e -> log.error("Error: {} ", e.getMessage()));
    }
    private Flux<StockDescriptionDetails> fetchApiData(WebClient client, String url) {
        // Print the raw response
        return client.get()
                .uri(url)
                .retrieve()
                .onStatus(status -> status.value() != 200, ClientResponse::createException)
                .bodyToMono(byte[].class)
                .map(StockDescriptionHttpEntryLoader::decompressGzip)
                .map(String::new)
                .flatMapMany(csvData -> Flux.fromIterable(convertCSVToList(csvData)))
                .doOnNext(stockDescriptionDetails -> log.info("Stock Description details. result: {} ", stockDescriptionDetails));
    }

    private static byte[] decompressGzip(byte[] compressed) {
        try (GZIPInputStream gis = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed));
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {
            return bufferedReader.lines().collect(Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decompress GZIP", e);
        }
    }

    private static StockDescriptionDetails convertResponseToDto(String jsonResponse) {
        //String jsonResponse = "{\"info\":{\"symbol\":\"RELIANCE\",\"companyName\":\"Reliance Industries Limited\",\"industry\":\"REFINERIES\",\"activeSeries\":[\"EQ\"],\"debtSeries\":[],\"isFNOSec\":true,\"isCASec\":false,\"isSLBSec\":true,\"isDebtSec\":false,\"isSuspended\":false,\"tempSuspendedSeries\":[],\"isETFSec\":false,\"isDelisted\":false,\"isin\":\"INE002A01018\",\"isMunicipalBond\":false,\"isTop10\":false,\"identifier\":\"RELIANCEEQN\"},\"metadata\":{\"series\":\"EQ\",\"symbol\":\"RELIANCE\",\"isin\":\"INE002A01018\",\"status\":\"Listed\",\"listingDate\":\"29-Nov-1995\",\"industry\":\"Refineries & Marketing\",\"lastUpdateTime\":\"26-Jun-2024 10:56:52\",\"pdSectorPe\":25.02,\"pdSymbolPe\":25.02,\"pdSectorInd\":\"NIFTY 500                                         \"},\"securityInfo\":{\"boardStatus\":\"Main\",\"tradingStatus\":\"Active\",\"tradingSegment\":\"Normal Market\",\"sessionNo\":\"-\",\"slb\":\"Yes\",\"classOfShare\":\"Equity\",\"derivatives\":\"Yes\",\"surveillance\":{\"surv\":null,\"desc\":null},\"faceValue\":10,\"issuedSize\":6765813926},\"sddDetails\":{\"SDDAuditor\":\"-\",\"SDDStatus\":\"-\"},\"priceInfo\":{\"lastPrice\":2947.7,\"change\":39.399999999999636,\"pChange\":1.3547433208403408,\"previousClose\":2908.3,\"open\":2892.1,\"close\":0,\"vwap\":2925.97,\"lowerCP\":\"2617.50\",\"upperCP\":\"3199.10\",\"pPriceBand\":\"No Band\",\"basePrice\":2908.3,\"intraDayHighLow\":{\"min\":2890.25,\"max\":2949.9,\"value\":2947.7},\"weekHighLow\":{\"min\":2220.3,\"minDate\":\"26-Oct-2023\",\"max\":3029,\"maxDate\":\"03-Jun-2024\",\"value\":2947.7},\"iNavValue\":null,\"checkINAV\":false},\"industryInfo\":{\"macro\":\"Energy\",\"sector\":\"Oil Gas & Consumable Fuels\",\"industry\":\"Petroleum Products\",\"basicIndustry\":\"Refineries & Marketing\"},\"preOpenMarket\":{\"preopen\":[{\"price\":2617.5,\"buyQty\":0,\"sellQty\":25},{\"price\":2618,\"buyQty\":0,\"sellQty\":15},{\"price\":2680,\"buyQty\":0,\"sellQty\":66},{\"price\":2762.9,\"buyQty\":0,\"sellQty\":5106},{\"price\":2892.1,\"buyQty\":0,\"sellQty\":0,\"iep\":true},{\"price\":3053.7,\"buyQty\":3578,\"sellQty\":0},{\"price\":3080,\"buyQty\":3,\"sellQty\":0},{\"price\":3100,\"buyQty\":1250,\"sellQty\":0},{\"price\":3199.1,\"buyQty\":401,\"sellQty\":0}],\"ato\":{\"buy\":6022,\"sell\":12452},\"IEP\":2892.1,\"totalTradedVolume\":119958,\"finalPrice\":2892.1,\"finalQuantity\":119958,\"lastUpdateTime\":\"26-Jun-2024 09:07:47\",\"totalBuyQuantity\":63088,\"totalSellQuantity\":131612,\"atoBuyQty\":6022,\"atoSellQty\":12452,\"Change\":-16.200000000000273,\"perChange\":-0.5570264415638095,\"prevClose\":2908.3}}"; // Replace with your actual JSON string
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        StockDescriptionDetails stockInfo = null;
        try {
            stockInfo = objectMapper.readValue(jsonResponse, StockDescriptionDetails.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stockInfo;
    }

}
