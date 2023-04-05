package searchengine.services;

import lombok.Setter;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.Repository.PageRepository;
import searchengine.Repository.SiteRepository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;

public class UrlRecursiveSearcher extends RecursiveTask<Integer> {
    private final SiteEntity site;
    private final String path;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    @Setter
    private static volatile boolean stopFlag = false;

    public UrlRecursiveSearcher(SiteEntity site, String path, PageRepository pageRepository, SiteRepository siteRepository) {

        this.site = site;
        this.path = path;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
    }

    @Override
    @SneakyThrows
    protected Integer compute() {
        if (stopFlag) {
            return 0;
        }
        Document doc;
        Elements links;
        String content = "";
        PageEntity page = new PageEntity();
        try {
            Thread.sleep(200);
            doc = Jsoup.connect(site.getUrl() + path).get();
            int code = doc.location().startsWith("https") ? 200 : 404;
            content = doc.toString();
            links = doc.select("a[href]");
            page.setSite(site);
            page.setPath(path);
            page.setCode(code);
            page.setContent(content);
            if (pageRepository.existsBySiteAndPath(site, path)) return 0;
            pageRepository.save(page);
        } catch (Exception e) {
            e.getMessage();
            return 0;
        }

        List<UrlRecursiveSearcher> subtasks = new ArrayList<>();

        for (Element link : links) {
            String href = link.attr("href");
            if (href.startsWith(site.getUrl())) {
                href = href.substring(site.getUrl().length());
            }
            if (href.equals("")) href = "/";
            Thread.sleep(200);
            if (!pageRepository.existsBySiteAndPath(site, href)) {
                if (stopFlag) {
                    return 0;
                }
                UrlRecursiveSearcher subtask = new UrlRecursiveSearcher(site, href, pageRepository, siteRepository);
                subtask.fork();
                subtasks.add(subtask);
            }
        }

        int count = 1;
        for (UrlRecursiveSearcher subtask : subtasks) {
            count += subtask.join();
        }

        return count;
    }
}

