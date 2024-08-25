package com.stock.dto;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
@Table("stock_description")
public class StockDescriptionDetails {

    @PrimaryKey
    private String symbol;

    @Column("company_name")
    private String companyName;

    @Column("series")
    private String series;

    @Column("dat_of_listing")
    private String dateOfListing;

    @Column("paid_up_value")
    private double paidUpValue;

    @Column("market_lot")
    private int marketLot;

    @Column("is_in_number")
    private String isInNumber;

    @Column("face_value")
    private int faceValue;

    @Column("created_date")
    private LocalDateTime createdDate;
}
