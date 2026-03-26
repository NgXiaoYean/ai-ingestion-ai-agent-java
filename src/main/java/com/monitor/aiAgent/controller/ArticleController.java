package com.monitor.aiAgent.controller;

import com.monitor.aiAgent.model.News;
import com.monitor.aiAgent.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    // trigger scraping
    // Behind is language= ? (ZH/EN/MS)
    @PostMapping("/scrape")
    public List<News> scrape() {
        return articleService.fetchAll();
    }

    // get all articles from DB
    @GetMapping
    public List<News> getAll() {
        return articleService.getAllArticle();
    }

}