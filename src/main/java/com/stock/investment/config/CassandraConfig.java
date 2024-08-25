package com.stock.investment.config;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;
import org.springframework.data.cassandra.core.cql.keyspace.CreateKeyspaceSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.KeyspaceOption;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

import java.util.Collections;
import java.util.List;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "spring.data.cassandra.keyspace-name")
@EnableCassandraRepositories(basePackages = "com.stock")
public class CassandraConfig extends AbstractCassandraConfiguration {

    private String keyspaceName = "realtime_stock_data";


    @SneakyThrows
    protected String getKeyspaceName() {
        /*log.error("Sleeping for 10 second in getKeyspaceName()");
        Thread.sleep(10000);*/
        return keyspaceName;
    }

    @SneakyThrows
    @Override
    protected List<CreateKeyspaceSpecification> getKeyspaceCreations() {
        /*log.error("Sleeping for 10 second in getKeyspaceCreations()");
        Thread.sleep(10000);*/
        return Collections.singletonList(CreateKeyspaceSpecification
                .createKeyspace(keyspaceName).ifNotExists()
                .with(KeyspaceOption.DURABLE_WRITES, true)
                .withSimpleReplication());
    }

    /*@SneakyThrows
    @Override
    protected List<String> getStartupScripts() {
        *//*log.error("Sleeping for 10 second in getStartupScripts()");
        Thread.sleep(10000);*//*
        return Collections.singletonList("CREATE KEYSPACE IF NOT EXISTS "
                + keyspaceName + " WITH replication = {"
                + " 'class': 'SimpleStrategy', "
                + " 'replication_factor': '3' " + "};");

    }*/
}
