package searchengine.services;

import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.model.PageEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlRecursiveSearcher extends RecursiveTask<List<PageEntity>> {
//public class UrlRecursiveSearcher extends RecursiveTask<String> {
    private String urlOrigin;
    private static AtomicInteger count = new AtomicInteger();
    @Setter
    private static SitesIndexService sitesIndexService;
    public static void setCount(int n) {
        count.set(n);
    }

    final String regex = "(?:(?:https?|ftp):\\/\\/|\\b(?:[a-z\\d]+\\.))(?:(?:[^\\s()<>]+|\\((?:[^\\s()<>]+|(?:\\(" +
            "[^\\s()<>]+\\)))?\\))+(?:\\((?:[^\\s()<>]+|(?:\\(?:[^\\s()<>]+\\)))?\\)|[^\\s`!()\\[\\]\\{\\};:'\".,<>?«»“”‘’]))?";
    @Setter
    private static String siteUrl;
//    private static SiteEntity site;
    @Setter
    private static int siteId;
    private List<String> copy;
    public UrlRecursiveSearcher(String url, List<String> copy) {
        this.urlOrigin = url;
        this.copy = copy;
    }

    @Override
    protected List<PageEntity> compute() {
//    protected String compute() {
        if (count.get() >= 50) return null;
        count.incrementAndGet();
        Document doc;
        try {
            Thread.sleep(200);
            doc = Jsoup.connect(urlOrigin).get();//.userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
//                    .referrer("http://www.google.com")
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }

        List<PageEntity> pageList = new ArrayList<>();

        PageEntity page = new PageEntity();
//        page.setSite(site);
//        page.setPath(urlOrigin);
//        page.setCode(200);
//        page.setContent(doc.text());

//        em.merge(page);
//        em.flush();
//        em.close();

        try {
            sitesIndexService.pageIndexing(siteId, urlOrigin, 200, doc.text());
        } catch (Exception e) {
            System.out.println("");
            e.getMessage();
        }
//        try {
//            pageRepository.save(page);
//        } catch (Exception e) {
//            e.printStackTrace();
//            System.out.println(doc.text());
//        }

//        pageList.add(page);

        Matcher matcher = Pattern.compile("/[A-Za-z0-9\\-_]+/[A-Za-z0-9\\-_/.]*")//("/[A-Za-z0-9\\-_]+/[^\"?=]*")
                .matcher(doc.body().toString());

        List<UrlRecursiveSearcher> taskList = new ArrayList<>();

        while (matcher.find()) {
            String url = doc.body().toString().substring(matcher.start(), matcher.end());

            if (!url.startsWith(siteUrl)) {
                url = siteUrl.concat(url);
            }

            if (url.matches(regex)//(".+\\.ru/.+\\..+")
//                    && !url.contains("#") && url.startsWith(site.getUrl())
                    && !copy.contains(url.trim()) && count.get() <= 50) {

                copy.add(url.trim());
                UrlRecursiveSearcher task = new UrlRecursiveSearcher(url, copy);
                task.fork();
                taskList.add(task);

                System.out.println("\t" + url + "\t" + count);
            }
        }
        for (RecursiveTask task : taskList) {
            if (task.join() != null) {
                pageList.addAll((List<PageEntity>) task.join());
            }
//            urlOrigin = (task.join().equals("")) ? urlOrigin : urlOrigin.concat("\n" + task.join());
        }

        return pageList;//urlOrigin;
    }
}

