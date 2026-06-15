package com.stock.entryloader;

import com.stock.dto.StockDescriptionDetails;
import com.stock.service.NseSessionManager;
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
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static com.stock.util.Utility.convertCSVToList;

@Slf4j
@Service
public class StockDescriptionHttpEntryLoader {

    private final NseSessionManager nseSessionManager;

    public StockDescriptionHttpEntryLoader(NseSessionManager nseSessionManager) {
        this.nseSessionManager = nseSessionManager;
    }

    private static final String URL = "https://archives.nseindia.com/content/equities/EQUITY_L.csv";
    private static final String BASE_URL = "https://www.nseindia.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    private static final String ACCEPT_LANGUAGE = "en-GB,en-US;q=0.9,en;q=0.8";
    private static final String ACCEPT_ENCODING = "gzip, deflate";

    private HttpClient buildHttpClient() {
        return HttpClient.create().followRedirect(true);
    }

    public Mono<List<StockDescriptionDetails>> getStockDetails() {
        // First, generate fresh NSE session cookie from warm-up call (no file needed)
        return nseSessionManager.generateFreshSessionCookie()
                .flatMap(sessionCookie -> {
                    WebClient.Builder builder = WebClient.builder()
                            .baseUrl(BASE_URL)
                            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE)
                            .defaultHeader(HttpHeaders.ACCEPT_ENCODING, ACCEPT_ENCODING)
                            .clientConnector(new ReactorClientHttpConnector(buildHttpClient()));

                    // Use the fresh session cookie from warm-up call
                    if (sessionCookie != null && !sessionCookie.isBlank()) {
                        builder.defaultHeader(HttpHeaders.COOKIE, sessionCookie);
                    }
                    WebClient client = builder.build();

                    return fetchApiData(client);
                })
                .onErrorMap(e -> new RuntimeException(
                        "NSE is currently unreachable. Check your internet connection. Error: " + e.getMessage(), e))
                .doOnError(e -> log.error("Failed to fetch stock descriptions from NSE: {}", e.getMessage()));
    }

    private Mono<List<StockDescriptionDetails>> fetchApiData(WebClient client) {
        return client.get()
                .uri(URL)
                .retrieve()
                .onStatus(status -> status.value() != 200, ClientResponse::createException)
                .bodyToMono(byte[].class)
                .map(StockDescriptionHttpEntryLoader::decodeResponse)
                .map(String::new)
                .flatMapMany(csvData -> Flux.fromIterable(convertCSVToList(csvData)))
                .collectList()
                .doOnNext(list -> log.info("Fetched {} stock descriptions from NSE.", list.size()));
    }

    private static byte[] decompressGzip(byte[] compressed) {
        try (GZIPInputStream gis = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed));
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {
            return bufferedReader.lines().collect(Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decompress GZIP", e);
        }
    }

    private static byte[] decodeResponse(byte[] responseBytes) {
        try {
            return decompressGzip(responseBytes);
        } catch (RuntimeException ignored) {
            return responseBytes;
        }
    }

}
