package com.monitor.aiAgent.model;

import lombok.Data;

@Data
public class FailureSample {
    private long timestamp;
    private String category;
    private String message;
    private String url;
}