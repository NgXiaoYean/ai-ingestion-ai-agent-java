package com.monitor.aiAgent.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@CompoundIndex(def = "{'title': 1, 'sourceName': 1, 'type': 1}", unique = true)
@Document(collection = "scrape_test", language = "none")
public class News {

    
    @Id
    private String id;

    private String recordId;

    private String videoUrl;

    private String imageUrl;

    private String title;

    private String content;

    private String category;

    private String author;

    private String language;

    private Long publishedAt;

    @Builder.Default
    private NewsType type = NewsType.NEWS;

    private String sourceName;  // BBC, SCMP, etc
    
    private String sourceId;

    private String sourceImage;

    private String sourceURL;   // BBC.com, SCMP.com, etc

    private String link;        //xxx.com/man-fallen-into-drugs

    private String rssLink;     // BBC.com/rss, SCMP.com, etc

    private String rssType;     // RSS or NewsData

    private String status;

    private Long createdAt;

}
