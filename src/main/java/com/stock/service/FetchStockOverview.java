package com.stock.service;

import com.stock.dto.StockInfoDetails;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Service
public class FetchStockOverview {


    /*public Mono<StockInfoDetails> fetchStockOverview() {

    }*/
    public static void main(String[] args) throws IOException {
        Document document = Jsoup.connect("https://www.moneycontrol.com/india/stockpricequote/trading/adanienterprises/AE13")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3")
                .get();

        Elements parentDiv = document.select(".nsestock_overview");
        Elements tableDivClassList = parentDiv.get(0).select(".oview_table");
        for (Element element : tableDivClassList) {
            Elements tableRow = element.select("table > tbody > tr");
            //element.select("table > tbody > tr").get(0).children().get(1).text();
            for (Element element1 : tableRow) {
                int size = element1.children().size();
                for (int i = 0; i < size; i++) {
                    System.out.println(element1.children().get(i).text());
                }
                //System.out.println(element1);
            }
            //System.out.println(element);
        }
    }


}
