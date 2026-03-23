package com.monitor.aiAgent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoClient;

@Configuration
public class MongoDbConfig {

    @Value("${spring.data.mongodb.uri:mongodb://localhost:27017}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database:ai_monitoring_test}")
    private String databaseName;

    @Bean
    public MongoTemplate mongoTemplate(MongoClient mongoClient) {
        System.out.println("========== MongoDB Configuration ==========");
        System.out.println("URI: " + mongoUri);
        System.out.println("Database Name: " + databaseName);
        System.out.println("==========================================");

        return new MongoTemplate(mongoClient, databaseName);
    }
}
