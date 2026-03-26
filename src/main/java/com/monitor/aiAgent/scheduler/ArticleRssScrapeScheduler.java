package com.monitor.aiAgent.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.monitor.aiAgent.service.ArticleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArticleRssScrapeScheduler {

    private final ArticleService articleService;

    @Scheduled(cron = "0 */30 * * * *")
    public void scrapeArticles() {
        log.info("Starting RSS scrape");

        articleService.fetchAll();

        articleService.sendCombinedCriticalAlert();

        log.info("RSS scraping finished");
    }
}
