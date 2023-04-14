package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.lemmatizer.LemmaFinder;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.error.ErrorResponse;
import searchengine.dto.seach.SearchData;
import searchengine.dto.seach.SearchResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final SitesList sites;
    @Autowired
    SiteRepository siteRepository;
    @Autowired
    PageRepository pageRepository;
    @Autowired
    LemmaRepository lemmaRepository;
    @Autowired
    IndexRepository indexRepository;

    @Override
    public ResponseEntity getSearch(String query, String site) {
        SearchResponse response = new SearchResponse();
        List<SearchData> data = new ArrayList<>();

        LemmaFinder lemmaFinder;
        try {
            lemmaFinder = LemmaFinder.getInstance();
        } catch (IOException e) {
            return returnResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Set<String> lemmas = lemmaFinder.getLemmaSet(query);

        System.out.println(lemmas);

        if (lemmas.isEmpty()) return returnResponse("Задан пустой поисковый запрос", HttpStatus.INTERNAL_SERVER_ERROR);

        List<SiteEntity> siteEntityList = new ArrayList<>();
        if (site.equals("")) {
            for (Site s : sites.getSites()) {
                siteEntityList.add(siteRepository.findByName(s.getName()));
            }
        } else {
            siteEntityList.add(siteRepository.findByUrl(site));
        }

        for (SiteEntity siteEntity : siteEntityList) {
            if (!siteEntity.getStatus().equals(Status.INDEXED)) continue;
            int pageCount = pageRepository.countAllBySite(siteEntity).get();

            List<LemmaEntity> lemmaEntityList = new ArrayList<>();

            for (String lemma : lemmas) {
                LemmaEntity lemmaEntity = lemmaRepository.findByLemmaAndSite(lemma, siteEntity);
                if (lemmaEntity != null && !((lemmaEntity.getFrequency() * 1.0) / pageCount > 1.8))
                    lemmaEntityList.add(lemmaEntity);
            }

            if (lemmaEntityList.size() == 0) {
                continue;
            }

            lemmaEntityList.sort((l1, l2) -> {
                if (l1.getFrequency() < l2.getFrequency()) return 1;
                return 0;
            });

            List<PageEntity> pages = new ArrayList<>();
            List<PageEntity> pageEntityList = new ArrayList<>();
            for (IndexEntity index : indexRepository.findByLemma(lemmaEntityList.get(0))) {
                pages.add(index.getPage());
                pageEntityList.add(index.getPage());
            }

            for (LemmaEntity l : lemmaEntityList) {
                for (PageEntity page : pages) {
                    if (!indexRepository.existsByPageAndLemma(page, l)) {
                        pageEntityList.remove(page);
                    }
                    if (pageEntityList.size() == 0) {
                        break;
                    }
                }
                if (pageEntityList.size() == 0) break;
            }

            if (pageEntityList.size() == 0) continue;

            int absRelevance;
            int totalRelevance = 0;

            for (PageEntity page : pageRepository.findAllBySite(siteEntity)) {
                for (LemmaEntity lemma : lemmaEntityList) {
                    if (indexRepository.existsByPageAndLemma(page, lemma))
                        totalRelevance += indexRepository.findByPageAndLemma(page, lemma).getRank();
                }
            }

            for (PageEntity page : pageEntityList) {
                absRelevance = 0;
                for (LemmaEntity lemma : lemmaEntityList) {
                    if (indexRepository.existsByPageAndLemma(page, lemma))
                        absRelevance += indexRepository.findByPageAndLemma(page, lemma).getRank();
                }
                double relevance = absRelevance * 1.0 / totalRelevance;
                SearchData searchData = collectSearchData(siteEntity, page, lemmaEntityList, relevance);
                data.add(searchData);
            }
        }

        data.sort((d1, d2) -> {
            if (d1.getRelevance() > d2.getRelevance()) return 1;
            return 0;
        });

        response.setData(data);
        response.setResult(true);
        response.setCount(response.getCount() + data.size());
        return ResponseEntity.ok(response);
    }

    private SearchData collectSearchData(SiteEntity siteEntity, PageEntity page, List<LemmaEntity> lemmaEntityList, double relevance) {
        SearchData searchData = new SearchData();

        searchData.setSite(siteEntity.getUrl());
        searchData.setSiteName(siteEntity.getName());
        searchData.setUri(page.getPath());
        String title = Jsoup.parse(page.getContent()).title();
        searchData.setTitle(title);

        String text = Jsoup.parse(page.getContent()).text();
        String snippet = "<html><body>\"<b>";
        for (LemmaEntity lemma : lemmaEntityList) {
            int index = text.indexOf(lemma.getLemma());
            int start = Math.max(0, index - 70);
            int end = Math.min(text.length(), index + lemma.getLemma().length() + 70);
            String textSnippet = text.substring(start, end);

            int lSpace = textSnippet.indexOf(" ");
            int lDot = textSnippet.indexOf(".");
            int rSpace = textSnippet.lastIndexOf(" ");
            int rDot = textSnippet.lastIndexOf(".");

            if (rSpace != -1) end = start + rSpace;
            else if (rDot != -1) end = start + rDot;
            if (lSpace != -1) start += lSpace + 1;
            else if (lDot != -1) start += lDot + 1;

            snippet = snippet.concat(text.substring(start, end) + "\n");
        }
        snippet = snippet.concat("</b>\"</body></html>");
        searchData.setSnippet(snippet);
        searchData.setRelevance(relevance);
        return searchData;
    }

    private ResponseEntity returnResponse(String error, HttpStatus status) {
        ErrorResponse response = new ErrorResponse();
        response.setResult(false);
        response.setError(error);
        return ResponseEntity.status(status).body(response);
    }
}
