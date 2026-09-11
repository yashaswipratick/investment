package com.stock.stock_analyser.fundamental;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Generic fundamental-data configuration; workbook mappings are discovered from the filesystem. */
@Component
@ConfigurationProperties(prefix = "fundamental")
public class FundamentalDataConfiguration {
    private String dataDirectory;
    private String workbookExtension = ".xlsx";

    public String getDataDirectory() { return dataDirectory; }
    public void setDataDirectory(String dataDirectory) { this.dataDirectory = dataDirectory; }
    public String getWorkbookExtension() { return workbookExtension; }
    public void setWorkbookExtension(String workbookExtension) { this.workbookExtension = workbookExtension; }
}
