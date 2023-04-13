package searchengine.services;

import org.springframework.http.ResponseEntity;

public interface SitesIndexService {
    ResponseEntity sitesIndexing();
    ResponseEntity stopIndexing();
    ResponseEntity pageIndexing(String url);
}
