package com.stock.service;

import com.stock.dto.IndexStockDetails;
import com.stock.dto.NiftyIndexStockDetails;
import com.stock.repository.NiftyIndexStockDetailsRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Collector;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FetchNiftyIndexStockDetailsService {

    @Autowired
    private NiftyIndexStockDetailsRepository niftyIndexStockDetailsRepository;

    private final static String URL = "https://www.moneycontrol.com/stocks/marketstats/indexcomp.php?optex=NSE&opttopic=indexcomp&index=9";
    private final static String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";

    public Mono<NiftyIndexStockDetails> fetchAndSave() throws IOException {
        return fetch()
                .flatMap(this::update);
    }

    private Mono<NiftyIndexStockDetails> fetch() throws IOException {
        Document document = Jsoup.connect(URL)
                .userAgent(USER_AGENT)
                .timeout(10 * 1000)
                .get();

        Elements tableBody = document.select("table > tbody > tr");
        tableBody.remove(0);
        Map<String, IndexStockDetails> map = new HashMap<>();

        return Flux.fromIterable(tableBody)
                .flatMap(tableBodyData -> {
                    log.info("change % for stock {}  -> {} ", tableBodyData.children().get(0).select("span > a").text(), tableBodyData.children().get(3).text());
                    IndexStockDetails data = IndexStockDetails.builder()
                            .companyName(tableBodyData.children().get(0).select("span > a").text())
                            .industry(tableBodyData.children().get(1).text())
                            .link(tableBodyData.children().get(0).select("span > a").attr("href"))
                            .lastPrice(tableBodyData.children().get(2).text().replaceAll(",", ""))
                            .change(!tableBodyData.children().get(3).text().equalsIgnoreCase("-")
                                    ? tableBodyData.children().get(3).text().replaceAll(",", "") : "0.0")
                            .percentageChange(!tableBodyData.children().get(3).text().equalsIgnoreCase("-")
                                    ? tableBodyData.children().get(4).text().replaceAll(",", "") : "0.0")
                            .marketCap(tableBodyData.children().get(5).text().replaceAll(",", ""))
                            .build();
                    return Mono.justOrEmpty(data);
                })
                .flatMap(indexStockDetails -> Mono.justOrEmpty(Pair.of(indexStockDetails.getCompanyName(), indexStockDetails)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                .flatMap(stringIndexStockDetailsMap -> Mono.justOrEmpty(NiftyIndexStockDetails.builder()
                        .date(LocalDate.now())
                        .indexStockDetailsMap(stringIndexStockDetailsMap)
                        .createdDate(LocalDateTime.now())
                        .build()));
    }

    public Mono<NiftyIndexStockDetails> save(NiftyIndexStockDetails details) {
        return niftyIndexStockDetailsRepository.save(details);
    }

    public Mono<Void> deleteById(LocalDate id) {
        return niftyIndexStockDetailsRepository.deleteById(id.toString());
    }

    public Mono<Void> delete(NiftyIndexStockDetails details) {
        return niftyIndexStockDetailsRepository.delete(details);
    }

    public Mono<NiftyIndexStockDetails> get(LocalDate id) {
        return niftyIndexStockDetailsRepository.findById(id.toString());
    }

    public Mono<NiftyIndexStockDetails> update(NiftyIndexStockDetails details) {
        return get(details.getDate())
                .switchIfEmpty(Mono.defer(() -> niftyIndexStockDetailsRepository.save(details)))
                .flatMap(existingDetail -> {
                    if (existingDetail == null || existingDetail.getCreatedDate() == null
                            || details.getCreatedDate().isAfter(existingDetail.getCreatedDate())) {
                        return niftyIndexStockDetailsRepository.save(details);
                    }
                    return Mono.empty();
                });
    }

}
