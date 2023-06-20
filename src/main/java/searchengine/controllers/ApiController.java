package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.SearchServiceImpl;
import searchengine.services.SitesIndexServicesImpl;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final StatisticsService statisticsService;
    private final SitesIndexServicesImpl sitesIndexService;
    private final SearchServiceImpl searchService;

    public ApiController(StatisticsService statisticsService, SitesIndexServicesImpl sitesIndexServices, SearchServiceImpl searchService) {
        this.statisticsService = statisticsService;
        this.sitesIndexService = sitesIndexServices;
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
    public ResponseEntity<SearchResponse> search(@RequestParam String query, @RequestParam int offset, @RequestParam int limit,
                                                 @RequestParam(required = false, defaultValue = "") String site) {
        return searchService.getSearch(query, site, offset, limit);
    }
}
