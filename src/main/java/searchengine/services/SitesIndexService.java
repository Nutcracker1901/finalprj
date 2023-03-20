package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mapping.AccessOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import searchengine.Repository.LemmaRepository;
import searchengine.Repository.PageRepository;
import searchengine.Repository.SiteRepository;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

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
    @PersistenceContext
    private EntityManager em;
    private LemmaFinder lemmaFinder;

//    private SiteEntity findSiteByName(String name) {
//        return em.createQuery("SELECT a from SiteEntity a where a.name = '" + name + "'", SiteEntity.class).getSingleResult();
//    }
//
    private void deletePagesBySite(SiteEntity site) {
        em.createQuery("DELETE from PageEntity where site = '" + site + "'", PageEntity.class);
    }

    @SneakyThrows
    public void sitesIndexing() {
        lemmaFinder = LemmaFinder.getInstance();

//        siteRepository.deleteAll();
//        pageRepository.deleteAll();
//        lemmaRepository.deleteAll();

        List<Site> sitesList = sites.getSites();
        List<String> copy = new ArrayList<>();
//        UrlRecursiveSearcher.setPageRepository(pageRepository);

        for (int i = 0; i < sitesList.size(); i++) {
            copy.clear();
            String url = sitesList.get(i).getUrl();
            copy.add(url);

//            SiteEntity site;
            SiteEntity site = siteRepository.findByName(sitesList.get(i).getName());

            if (site != null) {
                System.out.println("sleeeeeeeeeeeeeeeeeeeep");
//                delete(site);
//                pageRepository.deletePageEntitiesBySite(site);
//                lemmaRepository.deleteAllBySite(site);
//                siteRepository.delete(site);
//                em.getTransaction().commit();
//                deletePagesBySite(site);
//                Thread.sleep(60000);
            }

//            try {
//                System.out.println("Test");
//                site = findSiteByName(site.getName());
//                System.out.println(site.getId());
//                deletePagesBySite(site);
//                siteRepository.deleteById(site.getId());
//            } catch (Exception e) {
//                e.getMessage();
//            }

            site = new SiteEntity();
            site.setName(sitesList.get(i).getName());
            site.setUrl(url);
            site.setStatus(Status.INDEXING);
            site.setStatusTime(new Date());

            siteRepository.saveAndFlush(site);
//            em.getTransaction().commit();

//            Thread.sleep(60000);

            System.out.println("AFSGDHJGFDHFGFHGF\n" + site.getId());
//            UrlRecursiveSearcher.setSite(site);
            UrlRecursiveSearcher.setSiteUrl(site.getUrl());
            UrlRecursiveSearcher.setSiteId(site.getId());
            UrlRecursiveSearcher.setCount(0);

//            String str = new ForkJoinPool().invoke(new UrlRecursiveSearcher(url, copy));
            List<PageEntity> pageList = new ForkJoinPool().invoke(new UrlRecursiveSearcher(url, copy));
//            if (pageList != null)
//            for (PageEntity page : pageList)
//                try {
//                    pageRepository.save(page);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                pageRepository.saveAll(pageList);

            site.setStatusTime(new Date());
            site.setStatus(Status.INDEXED);

            siteRepository.save(site);
        }
    }

    @Transactional
    public void pageIndexing(int siteId, String path, int code, String content) {
        PageEntity page = new PageEntity();
//        System.out.println(siteRepository.findById(siteId).get().getId() + "  this is id" + siteRepository.findById(siteId).get().getUrl());
//        page.setSite(siteRepository.findById(siteId).get());
//        try {

//        page.setSite(em.merge(site));
        page.setSite(siteRepository.findById(siteId).get());
        page.setPath(path);
        page.setCode(200);
        page.setContent(content);
        pageRepository.saveAndFlush(page);
        System.out.println("YESSSSSSSSSSSSSSSS");

        Map<String, Integer> result = lemmaFinder.collectLemmas(content);
        LemmaEntity lemma;
        for (Map.Entry<String, Integer> entry : result.entrySet()) {
            if (lemmaRepository.existsByLemmaAndSite(entry.getKey(), siteRepository.findById(siteId).get())) {
                lemma = lemmaRepository.findByLemmaAndSite(entry.getKey(), siteRepository.findById(siteId).get());
            } else {
                lemma = new LemmaEntity();
                lemma.setLemma(entry.getKey());
                lemma.setSite(siteRepository.findById(siteId).get());
            }
            lemma.setFrequency(lemma.getFrequency() + entry.getValue());
            lemmaRepository.save(lemma);
        }
    }

    @Transactional
    public void pageIndexing(String url) {
        Document doc;
        try {
            doc = Jsoup.connect(url).get();
            System.out.println(doc.outerHtml() + "\n\n\n\n 1111111111111");
            System.out.println(doc.absUrl(url) + "\n\n\n\n 2222222222222");
            System.out.println(doc.baseUri() + "\n\n\n\n 3333333333333");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(SiteEntity site) {
        pageRepository.deletePageEntitiesBySite(site);
        lemmaRepository.deleteAllBySite(site);
        siteRepository.delete(site);
    }
}
