package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.SitesIndexService;
import searchengine.services.StatisticsService;
import searchengine.services.UrlRecursiveSearcher;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final StatisticsService statisticsService;
    private final SitesIndexService sitesIndexService;

    public ApiController(StatisticsService statisticsService, SitesIndexService sitesIndexService) {
        this.statisticsService = statisticsService;
        this.sitesIndexService = sitesIndexService;
        UrlRecursiveSearcher.setSitesIndexService(sitesIndexService);
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public void startIndexing() {
        sitesIndexService.sitesIndexing();
    }

    @PostMapping("/indexPage{url}")
    public void indexPage(@PathVariable String url) {
        sitesIndexService.pageIndexing(url);
    }
}
