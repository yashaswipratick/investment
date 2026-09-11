package com.stock.util;

import com.fasterxml.jackson.databind.ObjectMapper;
/*import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;*/
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class Test {

    public static final Set<String> stockSymbolCache = new HashSet<>();
    public static void main(String[] args) throws IOException {
        /*File input = new File("/Users/yashaswipratick/Documents/zerodha/23-07-2024/positions.csv");
        File output = new File("/Users/yashaswipratick/Documents/zerodha/23-07-2024/data.json");

        List<Map<?, ?>> data = readObjectsFromCsv(input);
        writeAsJson(data, output);*/
        readJsonFile();
    }

    /*public static List<Map<?, ?>> readObjectsFromCsv(File file) throws IOException {
        CsvSchema bootstrap = CsvSchema.emptySchema().withHeader();
        CsvMapper csvMapper = new CsvMapper();
        try (MappingIterator<Map<?, ?>> mappingIterator = csvMapper.readerFor(Map.class).with(bootstrap).readValues(file)) {
            return mappingIterator.readAll();
        }
    }*/

    public static List<String> readJsonFile() throws IOException {
        List<String> stockSymbols = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        // Read JSON file from classpath resources
        ClassPathResource resource = new ClassPathResource("postman/Nifty-50.json");
        InputStream inputStream = resource.getInputStream();
        NiftyData niftyData = mapper.readValue(
                inputStream,
                NiftyData.class
        );
        // Access data
        /*System.out.println("Name: " + niftyData.getName());
        System.out.println("Timestamp: " + niftyData.getTimestamp());
        System.out.println("Advances: " + niftyData.getAdvance().getAdvances());
        System.out.println("Declines: " + niftyData.getAdvance().getDeclines());*/

        // Print stock data
        niftyData.getData().forEach(stock -> {
            stockSymbols.add(stock.getSymbol());
            /*System.out.println("Symbol: " + stock.getSymbol());
            System.out.println("Last Price: " + stock.getLastPrice());
            System.out.println("Change: " + stock.getChange());
            //System.out.println("Chart Today Path: " + stock.getMeta().get("chartTodayPath"));
            System.out.println("---------------------------");*/
        });
        System.out.println(stockSymbols);
        /*JSONObject jsonObject = new JSONObject(s);
        JSONArray data = jsonObject.getJSONArray("data");
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = (JSONObject) data.get(i);
            String symbol = o.getString("symbol");
            stockSymbols.add(symbol);
        }*/
        return stockSymbols;
    }

    public static void writeAsJson(List<Map<?, ?>> data, File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String s = mapper.writeValueAsString(data);
        System.out.println(s);
        mapper.writeValue(file, data);
    }
}
