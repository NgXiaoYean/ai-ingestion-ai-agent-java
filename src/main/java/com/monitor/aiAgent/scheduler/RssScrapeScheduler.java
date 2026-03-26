package com.monitor.aiAgent.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.monitor.aiAgent.service.NewsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RssScrapeScheduler {

    private final NewsService newsService;

    @Scheduled(cron = "0 */30 * * * *")
    public void scrapeNews() {
        log.info("Starting News RSS scrape");

        newsService.fetchAll();

        newsService.sendCombinedCriticalAlert();

        log.info("News scraping finished");
    }
}
