package com.monitor.aiAgent.model;

import lombok.Data;

@Data
public class StatusHistory {
    private Number time;
    private String status;
    private String error;
}
