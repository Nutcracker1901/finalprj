package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.seach.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.SearchService;
import searchengine.services.SitesIndexService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final StatisticsService statisticsService;
    private final SitesIndexService sitesIndexService;
    private final SearchService searchService;

    public ApiController(StatisticsService statisticsService, SitesIndexService sitesIndexService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.sitesIndexService = sitesIndexService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity startIndexing() {
        sitesIndexService.deleteIndexed();
        return sitesIndexService.sitesIndexing();
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity stopIndexing() {
        return sitesIndexService.stopIndexing();
    }

    @PostMapping("/indexPage")
    public ResponseEntity indexPage(@RequestParam String url) {
        return sitesIndexService.pageIndexing(url);
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam String query, @RequestParam String offset, @RequestParam String limit,
                                                 @RequestParam(required = false, defaultValue = "") String site) {
        return searchService.getSearch(query, site);
    }
}
