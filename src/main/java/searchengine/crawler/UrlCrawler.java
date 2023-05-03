package searchengine.crawler;

import lombok.Setter;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;

public class UrlCrawler extends RecursiveTask<Integer> {
    private final SiteEntity site;
    private final String path;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    @Setter
    private static volatile boolean stopFlag = false;

    public UrlCrawler(SiteEntity site, String path, PageRepository pageRepository, SiteRepository siteRepository) {

        this.site = site;
        this.path = path;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
    }

    @Override
    @SneakyThrows
    protected Integer compute() {
        if (stopFlag) return 0;
        Elements links;
        try {
            if (pageRepository.existsBySiteAndPath(site, path)) return 0;
            links = addPage();
        } catch (Exception e) {
            e.getMessage();
            return 0;
        }
        List<UrlCrawler> subtasks = new ArrayList<>();
        if (subtasksFork(links, subtasks)) return 0;

        int count = 1;
        for (UrlCrawler subtask : subtasks) {
            count += subtask.join();
        }
        return count;
    }

    private boolean subtasksFork(Elements links, List<UrlCrawler> subtasks) throws InterruptedException {
        for (Element link : links) {
            String href = link.attr("href");
            if (href.startsWith(site.getUrl())) {
                href = href.substring(site.getUrl().length());
            }
            if (href.equals("")) href = "/";
            Thread.sleep(200);
            if (!pageRepository.existsBySiteAndPath(site, href)) {
                if (stopFlag) return stopFlag;
                UrlCrawler subtask = new UrlCrawler(site, href, pageRepository, siteRepository);
                subtask.fork();
                subtasks.add(subtask);
            }
        }
        return stopFlag;
    }

    private Elements addPage() throws Exception {
        Document doc;
        Elements links;
        String content = "";
        Thread.sleep(200);
        doc = Jsoup.connect(site.getUrl() + path).get();
        int code = doc.location().startsWith("https") ? 200 : 404;
        content = doc.toString();
        links = doc.select("a[href]");
        PageEntity page = new PageEntity(site, path, code, content);
        if (pageRepository.existsBySiteAndPath(site, path)) return new Elements();
        pageRepository.save(page);
        return links;
    }
}

