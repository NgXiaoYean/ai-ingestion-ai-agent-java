package com.monitor.aiAgent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "news")
@Getter
@Setter
public class NewsConfig {

    private List<Source> sources;

    @Getter @Setter
    public static class Source {
        private String id;
        private String name;
        private String image;
        private String domainUrl;
        private String providerState;
        private List<Feed> feeds;
        private String language;
    }

    @Getter @Setter
    public static class Feed {
        private String url;
        private String category; // optional
        private String language;
    }
}
