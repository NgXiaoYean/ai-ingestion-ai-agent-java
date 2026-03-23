package com.monitor.aiAgent.dto;

import lombok.Data;
import java.util.List;

@Data
public class NewsDataItem {
    private String title;
    private String link;
    private List<String> creator;
    private String video_url;
    private String description;
    private String content;
    private String pubDate;
    private String image_url;
    private String source_id;
    private String source_url;
    private String source_icon;
    private String source_name;
    private String language;
    private List<String> category;
}
