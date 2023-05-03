package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.crawler.UrlCrawler;
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
        urlCrawlerActive();

        ResponseEntity response = multithreadingIndexing();
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

    private ResponseEntity multithreadingIndexing() {
        ExecutorService service = Executors.newFixedThreadPool(sites.getSites().size());
        multithreadingPageIndexing(service);
        if (stopFlag) return returnResponse("Индексация остановлена пользователем", HttpStatus.OK);

        ResponseEntity response = checkThreads(service);
        if (response != null) return response;

        service = Executors.newFixedThreadPool(sites.getSites().size());

        List<SiteEntity> siteEntityList = siteRepository.findAll();
        multithreadingLemmaIndexing(service, siteEntityList);

        if (stopFlag) return returnResponse("Индексация остановлена пользователем", HttpStatus.OK);
        response = checkThreads(service);
        return response;
    }

    private void multithreadingLemmaIndexing(ExecutorService service, List<SiteEntity> siteEntityList) {
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
    }

    private void multithreadingPageIndexing(ExecutorService service) {
        for (Site value : sites.getSites()) {
            Runnable task = () -> {
                SiteEntity site = new SiteEntity(value.getUrl(), value.getName());
                siteRepository.saveAndFlush(site);

                int count = new ForkJoinPool().invoke(new UrlCrawler(site, "/", pageRepository, siteRepository));
                if (stopFlag) return;

                updateSiteStatus(site, Status.INDEXING);
            };
            service.submit(task);
        }
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

            IndexEntity index = new IndexEntity(page, lemma, entry.getValue());
            indexRepository.save(index);
        }
    }

    @Override
    @Transactional
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
        updateSiteStatus(site, Status.INDEXING);

        try {
            page = indexPage(url, path, site);
        } catch (Exception e) {
            updateSiteStatus(site, Status.FAILED, e.getMessage());
            return returnResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        lemmaIndexing(site, page);
        updateSiteStatus(site, Status.INDEXED);

        return ResponseEntity.ok(new SuccessResponse());
    }

    private PageEntity indexPage(String url, String path, SiteEntity site) throws IOException {
        PageEntity page;
        Document doc;
        String content = "";
        doc = Jsoup.connect(url).get();
        int code = doc.location().startsWith("https") ? 200 : 404;
        content = doc.toString();
        page = new PageEntity(site, path, code, content);
        pageRepository.saveAndFlush(page);
        return page;
    }

    public String isPageIndexed(String url) {
        String baseUrl = "";
        String path = "/";
        for (Site value : sites.getSites()) {
            System.out.println("1");
            if (url.startsWith(value.getUrl())) {
                System.out.println("не происходит");
                baseUrl = value.getUrl();
                path = url.substring(value.getUrl().length());
            }
        }
        if (!siteRepository.existsByUrl(baseUrl)) {
            System.out.println("yes");
            return "";
        }

        return path;
    }

    @Transactional
    public void deleteByPageIndex(String url, String path) {
        SiteEntity site = siteRepository.findByUrl(url);
        updateSiteStatus(site, Status.INDEXING);
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
        urlCrawlerStop();

        List<SiteEntity> sitesList = siteRepository.findAll();
        for (SiteEntity site : sitesList) {
            updateSiteStatus(site, Status.FAILED, "Индексация остановлена пользователем");
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

    private void urlCrawlerStop() {
        UrlCrawler.setStopFlag(true);
    }

    private void urlCrawlerActive() {
        UrlCrawler.setStopFlag(false);
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
}
