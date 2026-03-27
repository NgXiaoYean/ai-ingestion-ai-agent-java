package com.monitor.aiAgent.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class CompactSourceResult {
    private String sourceName; // [0]
    private int healthScore; // [1]
    private String alertLevel; // [2]
    private int postsToday; // [3]
    private int avgPosts7Days; // [4]
    private double ingestionRatio; // [5]
    private int successRate7Days; // [6]
    private int consecutiveFailCount; // [7]
    private String primaryError; // [8]
}