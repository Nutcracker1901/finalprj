package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.crawler.UrlRecursiveSearcher;
import searchengine.lemmatizer.LemmaFinder;
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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SitesIndexServicesImpl implements SitesIndexService {
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

    @Override
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
                SiteEntity site = createSiteEntity(value);
                siteRepository.saveAndFlush(site);

                int count = new ForkJoinPool().invoke(new UrlRecursiveSearcher(site, "/", pageRepository, siteRepository));
                if (stopFlag) return;

                updateSiteStatus(site, Status.INDEXING);
            };
            service.submit(task);
        }
        if (stopFlag) return returnResponse("Индексация остановлена пользователем", HttpStatus.OK);

        ResponseEntity response = checkThreads(service);
        if (response != null) return response;

        service = Executors.newFixedThreadPool(sites.getSites().size());

        List<SiteEntity> siteEntityList = siteRepository.findAll();
        for (SiteEntity site : siteEntityList) {
            Runnable task = () -> {
                if (stopFlag) return;
                List<PageEntity> pages = pageRepository.findAllBySite(site);
                for (PageEntity page : pages) {
                    lemmaIndexing(site, page);
                }

                updateSiteStatus(site, Status.INDEXED);
            };
            service.submit(task);
        }

        response = checkThreads(service);
        if (response != null) return response;
        indexing = false;
        return ResponseEntity.ok(new SuccessResponse());
    }

    private ResponseEntity checkThreads(ExecutorService service) {
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

    @Override
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
        PageEntity page;

        try {
            page = createPageEntity(url, site, path);
            pageRepository.saveAndFlush(page);
            updateSiteStatus(site, Status.INDEXING);
        } catch (Exception e) {
            updateSiteStatus(site, Status.FAILED, e.getMessage());
            return returnResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        lemmaIndexing(site, page);
        updateSiteStatus(site, Status.INDEXED);

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

    @Override
    public ResponseEntity stopIndexing() {
        if (!isIndexing()) {
            return returnResponse("Индексация не запущена", HttpStatus.METHOD_NOT_ALLOWED);
        }
        stopFlag = true;
        indexing = false;
        urlSearcherStop();

        List<SiteEntity> sitesList = siteRepository.findAll();
        for (SiteEntity site : sitesList) {
            updateSiteStatus(site, Status.INDEXED, "Индексация остановлена пользователем");
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

    private void updateSiteStatus(SiteEntity site, Status status) {
        site.setStatusTime(new Date());
        site.setStatus(status);
        siteRepository.saveAndFlush(site);
    }

    private void updateSiteStatus(SiteEntity site, Status status, String error) {
        site.setStatusTime(new Date());
        site.setStatus(status);
        site.setLastError(error);
        siteRepository.saveAndFlush(site);
    }

    private SiteEntity createSiteEntity(Site value) {
        SiteEntity site = new SiteEntity();
        site.setUrl(value.getUrl());
        site.setName(value.getName());
        site.setStatus(Status.INDEXING);
        site.setStatusTime(new Date());
        return site;
    }

    private PageEntity createPageEntity(String url, SiteEntity site, String path) throws Exception {
        Document doc;
        String content = "";
        PageEntity page = new PageEntity();

        doc = Jsoup.connect(url).get();
        int code = doc.location().startsWith("https") ? 200 : 404;
        content = doc.toString();
        page.setSite(site);
        page.setPath(path);
        page.setCode(code);
        page.setContent(content);

        return page;
    }
}
