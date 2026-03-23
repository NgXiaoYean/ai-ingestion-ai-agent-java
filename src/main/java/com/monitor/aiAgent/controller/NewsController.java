package com.monitor.aiAgent.controller;

import com.monitor.aiAgent.model.News;
import com.monitor.aiAgent.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @PostMapping("/scrape")
    public List<News> scrape(@RequestParam String language) {
        return newsService.fetchAll(language);
    }
}
