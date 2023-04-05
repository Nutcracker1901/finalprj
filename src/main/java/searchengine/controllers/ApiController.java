package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.error.ErrorResponse;
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
        sitesIndexService.indexingActive();
        sitesIndexService.deleteIndexed();
        return sitesIndexService.sitesIndexing();
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity stopIndexing() {
        if (!sitesIndexService.isIndexing()) {
            ErrorResponse response = new ErrorResponse();
            response.setResult(false);
            response.setError("Индексация не запущена");
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
        }
        sitesIndexService.stopIndexing();
        return sitesIndexService.sitesIndexingStop();
    }

    @PostMapping("/indexPage")
    public ResponseEntity indexPage(@RequestParam String url) {
        String path = sitesIndexService.IsPageIndexed(url);
        if (path.equals("")) {
            ErrorResponse response = new ErrorResponse();
            response.setResult(false);
            response.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return ResponseEntity.ok(response);
        }
        url = url.substring(0, url.length() - path.length());
        sitesIndexService.deleteByPageIndex(url, path);
        return sitesIndexService.pageIndexing(url, path);
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam String query, @RequestParam String offset, @RequestParam String limit,
                                                 @RequestParam(required = false, defaultValue = "") String site) {
        return searchService.getSearch(query, site);
    }
}
