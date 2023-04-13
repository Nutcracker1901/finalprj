package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sites;
    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;

    @Override
    @SneakyThrows
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        List<SiteEntity> siteEntityList = new ArrayList<>();
        for (Site site : sites.getSites()) {
            SiteEntity siteEntity = siteRepository.findByName(site.getName());
            if (siteEntity != null) siteEntityList.add(siteEntity);
        }

        detailed = collectItems(siteEntityList);
        detailed.forEach(i -> {
            total.setPages(total.getPages() + i.getPages());
            total.setLemmas(total.getLemmas() + i.getLemmas());
        });
//        for (SiteEntity site : siteEntityList) {
//            DetailedStatisticsItem item = new DetailedStatisticsItem();
//            item.setName(site.getName());
//            item.setUrl(site.getUrl());
//            int pages = pageRepository.countAllBySite(site).orElse(0);
//            int lemmas = lemmaRepository.countAllBySite(site).orElse(0);
//            String status = site.getStatus().toString();
//            Date statusTime = site.getStatusTime();
//            item.setPages(pages);
//            item.setLemmas(lemmas);
//            item.setStatus(status);
//            if (site.getLastError() == null)
//                item.setError(site.getLastError());
//            item.setStatusTime(statusTime);
//            total.setPages(total.getPages() + pages);
//            total.setLemmas(total.getLemmas() + lemmas);
//            detailed.add(item);
//        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    private List<DetailedStatisticsItem> collectItems(List<SiteEntity> siteEntityList) {
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        for (SiteEntity site : siteEntityList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            int pages = pageRepository.countAllBySite(site).orElse(0);
            int lemmas = lemmaRepository.countAllBySite(site).orElse(0);
            String status = site.getStatus().toString();
            Date statusTime = site.getStatusTime();
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(status);
            if (site.getLastError() == null)
                item.setError(site.getLastError());
            item.setStatusTime(statusTime);
            detailed.add(item);
        }
        return detailed;
    }
}
