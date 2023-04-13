package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.error.ErrorResponse;
import searchengine.dto.success.SuccessResponse;
import searchengine.model.*;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SitesIndexService {
    private final SitesList sites;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;
    @PersistenceContext
    private EntityManager em;
    private volatile boolean stopFlag = false;
    private volatile boolean indexing = false;
    private LemmaFinder lemmaFinder;

    public ResponseEntity sitesIndexing() {
        if (isIndexing()) {
            return returnResponse("Индексация уже происходит", HttpStatus.METHOD_NOT_ALLOWED);
        }
        deleteIndexed();
        indexing = true;
        stopFlag = false;
        try {
            lemmaFinder = LemmaFinder.getInstance();
        } catch (IOException e) {
            return returnResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        urlSearcherActive();

        ExecutorService service = Executors.newFixedThreadPool(sites.getSites().size());
        for (Site value : sites.getSites()) {
            Runnable task = () -> {
                SiteEntity site = new SiteEntity();
                site.setUrl(value.getUrl());
                site.setName(value.getName());
                site.setStatus(Status.INDEXING);
                site.setStatusTime(new Date());
                siteRepository.saveAndFlush(site);

                if (stopFlag) {
                    return;
                }
                int count = new ForkJoinPool().invoke(new UrlRecursiveSearcher(site, "/", pageRepository, siteRepository));

                if (stopFlag) {
                    return;
                }

                site.setStatusTime(new Date());
                siteRepository.saveAndFlush(site);
            };
            service.submit(task);
        }
        if (stopFlag) {
            return returnResponse("Индексация остановлена пользователем", HttpStatus.OK);
        }

        ResponseEntity response = untilServiceDone(service);
        if (response != null) return response;

        service = Executors.newFixedThreadPool(sites.getSites().size());

        List<SiteEntity> siteEntityList = siteRepository.findAll();
        for (SiteEntity site : siteEntityList) {
            Runnable task = () -> {
                if (stopFlag) {
                    return;
                }
                List<PageEntity> pages = pageRepository.findAllBySite(site);
                for (PageEntity page : pages) {
                    lemmaIndexing(site, page);
                }

                site.setStatusTime(new Date());
                site.setStatus(Status.INDEXED);
                siteRepository.saveAndFlush(site);
            };
            service.submit(task);
        }

        response = untilServiceDone(service);
        if (response != null) return response;
        indexing = false;
        return ResponseEntity.ok(new SuccessResponse());
    }

    private ResponseEntity untilServiceDone(ExecutorService service) {
        try {
            service.shutdown();
            if (!service.awaitTermination(30, TimeUnit.MINUTES)) service.shutdownNow();

        } catch (InterruptedException e) {
            return returnResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return null;
    }

    @Transactional
    public void deleteIndexed() {
        if (isIndexing()) return;
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();

        for (Site value : sites.getSites()) {
            if (siteRepository.existsByName(value.getName())) {
                SiteEntity site;
                site = siteRepository.findByName(value.getName());
                pageRepository.deleteAllBySite(site);
                siteRepository.delete(site);
            }
        }
    }

    public void lemmaIndexing(SiteEntity site, PageEntity page) {
        String content = Jsoup.parse(page.getContent()).text();
        Map<String, Integer> result = lemmaFinder.collectLemmas(content);
        LemmaEntity lemma;
        for (Map.Entry<String, Integer> entry : result.entrySet()) {
            if (stopFlag) {
                return;
            }
            if (lemmaRepository.existsByLemmaAndSite(entry.getKey(), site)) {
                lemma = lemmaRepository.findByLemmaAndSite(entry.getKey(), site);
            } else {
                lemma = new LemmaEntity();
                lemma.setLemma(entry.getKey());
                lemma.setSite(siteRepository.findById(site.getId()).get());
            }
            lemma.setFrequency(lemma.getFrequency() + 1);
            lemmaRepository.save(lemma);

            IndexEntity index = new IndexEntity();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank(entry.getValue());
            indexRepository.save(index);
        }
    }

    public ResponseEntity pageIndexing(String url) {//, String path) {
        String path = isPageIndexed(url);
        if (path.equals("")) {
            return returnResponse("Данная страница находится за пределами сайтов, указанных в конфигурационном файле", HttpStatus.OK);
        }
        try {
            lemmaFinder = LemmaFinder.getInstance();
        } catch (IOException e) {
            return returnResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        url = url.substring(0, url.length() - path.length());
        deleteByPageIndex(url, path);

        SiteEntity site = siteRepository.findByUrl(url);

        Document doc;
        String content = "";
        PageEntity page = new PageEntity();

        try {
            doc = Jsoup.connect(url).get();
            int code = doc.location().startsWith("https") ? 200 : 404;
            content = doc.toString();
            page.setSite(site);
            page.setPath(path);
            page.setCode(code);
            page.setContent(content);
            pageRepository.saveAndFlush(page);
        } catch (Exception e) {
            site.setStatusTime(new Date());
            site.setStatus(Status.FAILED);
            return returnResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        lemmaIndexing(site, page);

        site.setStatusTime(new Date());
        site.setStatus(Status.INDEXED);
        siteRepository.saveAndFlush(site);

        return ResponseEntity.ok(new SuccessResponse());
    }

    public String isPageIndexed(String url) {
        String baseUrl = "";
        String path = "/";
        for (Site value : sites.getSites()) {
            if (url.startsWith(value.getUrl()))
                baseUrl = url.substring(0, value.getUrl().length());
            path = url.substring(value.getUrl().length() - 3);
        }
        if (!siteRepository.existsByUrl(baseUrl)) {
            return "";
        }

        return path;
    }

    @Transactional
    public void deleteByPageIndex(String url, String path) {
        SiteEntity site = siteRepository.findByUrl(url);
        site.setStatus(Status.INDEXING);
        site.setStatusTime(new Date());
        siteRepository.saveAndFlush(site);

        if (pageRepository.existsBySiteAndPath(site, path + "/"))
            path = path.concat("/");

        if (pageRepository.existsBySiteAndPath(site, path)) {
            PageEntity page = pageRepository.findBySiteAndPath(site, path);
            List<LemmaEntity> lemmaList = new ArrayList<>();
            List<IndexEntity> indexList = indexRepository.findByPage(page);
            for (IndexEntity index : indexList) {
                lemmaList.add(index.getLemma());
            }

            indexRepository.deleteAllByPage(page);

            for (LemmaEntity lemma : lemmaList) {
                if (lemma.getFrequency() > 1) lemma.setFrequency(lemma.getFrequency() - 1);
                else lemmaRepository.delete(lemma);
            }

            pageRepository.delete(page);
        }
    }

    public ResponseEntity stopIndexing() {
        if (!isIndexing()) {
            return returnResponse("Индексация не запущена", HttpStatus.METHOD_NOT_ALLOWED);
        }
        stopFlag = true;
        indexing = false;
        urlSearcherStop();

        List<SiteEntity> sitesList = siteRepository.findAll();
        for (SiteEntity site : sitesList) {
            site.setStatus(Status.FAILED);
            site.setLastError("Индексация остановлена пользователем");
            site.setStatusTime(new Date());
            siteRepository.save(site);
        }
        return ResponseEntity.ok(new SuccessResponse());
    }

    private ResponseEntity returnResponse(String error, HttpStatus status) {
        ErrorResponse response = new ErrorResponse();
        response.setResult(false);
        response.setError(error);
        return ResponseEntity.status(status).body(response);
    }

    public boolean isIndexing() {
        return indexing;
    }

    private void urlSearcherStop() {
        UrlRecursiveSearcher.setStopFlag(true);
    }

    private void urlSearcherActive() {
        UrlRecursiveSearcher.setStopFlag(false);
    }
}
