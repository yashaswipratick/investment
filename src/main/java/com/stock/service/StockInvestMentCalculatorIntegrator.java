package com.stock.service;

import com.stock.dto.Positions;
import com.stock.dto.StockInvestmentCalculatorDetails;
import com.stock.dto.StockInvestmentRequestDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
public class StockInvestMentCalculatorIntegrator {

    @Autowired
    private StockInvestmentCalculatorService service;

    public Mono<List<StockInvestmentCalculatorDetails>> calculate(StockInvestmentRequestDetails stockInvestmentRequestDetails) {
        if (CollectionUtils.isEmpty(stockInvestmentRequestDetails.getStockNames())) {
            log.error("positions details are null. skipping save...");
            return Mono.empty();
        }
        return service.calculate(stockInvestmentRequestDetails);
    }
}
