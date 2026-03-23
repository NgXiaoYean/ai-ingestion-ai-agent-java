package com.monitor.aiAgent.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoDbInitializer implements CommandLineRunner {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) throws Exception {
        String dbName = mongoTemplate.getDb().getName();
        System.out.println("=== MongoDB Initialization ===");
        System.out.println("Current Database: " + dbName);
        System.out.println("Configured Database: ai_monitoring_test");
        System.out.println("==============================");

        // Force database switch to ai_monitoring_test
        if (!dbName.equals("ai_monitoring_test")) {
            System.out.println("WARNING: Connected to '" + dbName + "' instead of 'ai_monitoring_test'");
            System.out.println("This usually means the database doesn't exist yet.");
            System.out.println("MongoDB will create it once you insert the first document.");
        }
    }
}
