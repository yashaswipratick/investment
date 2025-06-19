package com.stock.service;

import com.stock.dto.PriceInfo;
import com.stock.dto.StockInvestmentCalculatorDetails;
import com.stock.dto.StockInvestmentRequestDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StockInvestmentCalculatorService {

    @Autowired
    private StockDetailsService stockDetailsService;

    public Mono<List<StockInvestmentCalculatorDetails>> calculate(StockInvestmentRequestDetails stockInvestmentRequestDetails) {
        if (CollectionUtils.isEmpty(stockInvestmentRequestDetails.getStockNames())) {
            log.error("List can't be null. skipping get for positions");
            return Mono.empty();
        }

        double investmentAmountPerStock = Double.parseDouble(stockInvestmentRequestDetails.getInvestmentAmount()) / stockInvestmentRequestDetails.getStockNames().size();
        return Flux.fromIterable(stockInvestmentRequestDetails.getStockNames())
                .flatMap(stockSymbol -> stockDetailsService.getByStockSymbol(stockSymbol)
                        .flatMap(stockInfoDTO -> {
                            Map<String, PriceInfo> map = new HashMap<>();
                            map.put(stockInfoDTO.getInfo().getSymbol(), stockInfoDTO.getPriceInfo());
                            return Mono.justOrEmpty(map);
                        }))
                .flatMap(priceInfos -> calculate(priceInfos, investmentAmountPerStock))
                .flatMapIterable(stockInvestmentCalculatorDetails -> stockInvestmentCalculatorDetails)
                .collect(Collectors.toList());
    }

    private Mono<List<StockInvestmentCalculatorDetails>> calculate(Map<String, PriceInfo> stockPriceInfo, double investmentAmount) {
        if (stockPriceInfo.isEmpty()) {
            return Mono.empty();
        }

        //double investmentAmountPerStock = Double.parseDouble(investmentAmount) / stockPriceInfo.size();
        log.error("Investment amount per stock: stockIA: {}, TIA: {} ", investmentAmount, investmentAmount);
        log.error("IstockPrice Info Size {} ", stockPriceInfo.size());

        return Flux.fromIterable(stockPriceInfo.entrySet())
                .map(entry -> {
                    PriceInfo priceInfo = entry.getValue();
                    double lastPrice = Double.parseDouble(priceInfo.getLastPrice());
                    double weekHigh = Double.parseDouble(priceInfo.getWeekHighLow().getMax());
                    double weekLow = Double.parseDouble(priceInfo.getWeekHighLow().getMin());
                    double lowerBuyingLimit = ((weekHigh - weekLow) / 2) + weekLow;
                    double upperBuyingLimit = weekHigh - ((weekHigh - weekLow) / 2);


                    return StockInvestmentCalculatorDetails.builder()
                            .stockName(entry.getKey())
                            .price(priceInfo.getLastPrice())
                            .investmentAmount(investmentAmount)
                            .numberOfStocks(Math.round(investmentAmount / lastPrice))
                            .roundUpInvestmentAmount(lastPrice * Math.round(investmentAmount / lastPrice))
                            .fiftyTwoWeeksHighPrice(weekHigh)
                            .fiftyTwoWeeksLowPrice(weekLow)
                            .lowerBuyingLimit(lowerBuyingLimit)
                            .upperBuyingLimit(upperBuyingLimit)
                            .shouldBuy(lastPrice < upperBuyingLimit)
                            .build();
                })
                .collectList(); // Collect the details into a list
    }
}
