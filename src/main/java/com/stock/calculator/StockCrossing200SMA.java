package com.stock.calculator;

import com.stock.dto.StockHistoryDetails;
import com.stock.service.StockHistoryDataIntegrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
public class StockCrossing200SMA {

    @Autowired
    private StockHistoryDataIntegrator stockHistoryDataIntegrator;



}
