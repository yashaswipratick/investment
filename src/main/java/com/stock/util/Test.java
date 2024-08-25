package com.stock.util;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Test {

    public static void main(String[] args) throws IOException {
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
