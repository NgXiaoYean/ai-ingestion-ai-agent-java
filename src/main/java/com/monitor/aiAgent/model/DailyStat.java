package com.monitor.aiAgent.model;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class DailyStat {
    private String date;
    private int total;
    private int failed;
    private int success;
    private Map<String, Integer> failureCategoryCounts = new HashMap<>();
}