package com.stock.service;

import com.stock.dto.IndexStockDetails;
import com.stock.dto.NiftyIndexStockDetails;
import com.stock.repository.NiftyIndexStockDetailsRepository;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InvestmentIntegrator {

    @Autowired
    private InvestmentService service;




    public Mono<Map<String, Double>> invest(BigInteger amount) {
        return service.invest(amount);
    }


}
