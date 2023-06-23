package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public ResponseEntity getSearch(String query, String site, int offset, int limit) {
        List<SearchData> data = new ArrayList<>();

        LemmaFinder lemmaFinder;
        try {
            lemmaFinder = LemmaFinder.getInstance();
        } catch (IOException e) {
            return returnResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Set<String> lemmas = lemmaFinder.getLemmaSet(query);
        if (lemmas.isEmpty()) return returnResponse("Задан пустой поисковый запрос", HttpStatus.INTERNAL_SERVER_ERROR);

        List<SiteEntity> siteEntityList = new ArrayList<>();
        if (site.equals("")) {
            for (Site s : sites.getSites()) siteEntityList.add(siteRepository.findByName(s.getName()));
        } else siteEntityList.add(siteRepository.findByUrl(site));

        for (SiteEntity siteEntity : siteEntityList) {
            data.addAll(collectSiteSearchData(siteEntity, lemmas));
        }
        data.sort(Comparator.comparingDouble(SearchData::getRelevance).reversed());
        SearchResponse response = getPaginatedSearchResponse(offset, limit, data);
        return ResponseEntity.ok(response);
    }

    private static SearchResponse getPaginatedSearchResponse(int offset, int limit, List<SearchData> data) {
        Pageable pageable = PageRequest.of(offset, limit);
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), data.size());
        Page<SearchData> page = new PageImpl<>(data.subList(start, end), pageable, data.size());

        SearchResponse response = new SearchResponse(true, (int) page.getTotalElements(), page.getContent());
        return response;
    }

    private SearchData collectPageSearchData(SiteEntity siteEntity, PageEntity page, List<LemmaEntity> lemmaEntityList, double relevance) {
        SearchData searchData = new SearchData();
        searchData.setSite(siteEntity.getUrl());
        searchData.setSiteName(siteEntity.getName());
        searchData.setUri(page.getPath());
        String title = Jsoup.parse(page.getContent()).title();
        searchData.setTitle(title);

        String text = Jsoup.parse(page.getContent()).text();
        String snippet = getSnippet(lemmaEntityList, text);
        searchData.setSnippet(snippet);
        searchData.setRelevance(relevance);
        return searchData;
    }

    private static String getSnippet(List<LemmaEntity> lemmaEntityList, String text) {
        String snippet = "<html><body>\"";
        List<LemmaEntity> lemmaEntities = lemmaEntityList;
        if (lemmaEntityList.size() > 3) lemmaEntityList = lemmaEntityList.subList(0, 3);
        for (LemmaEntity lemma : lemmaEntityList) {
            int index = text.toLowerCase().indexOf(lemma.getLemma());
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
            snippet = snippet.replaceAll ("(?i)" + Pattern.quote (lemma.getLemma()), "<b>" + lemma.getLemma() + "</b>");
        }
        snippet = snippet.concat("\"</body></html>");
        return snippet;
    }

    private List<SearchData> collectSiteSearchData(SiteEntity siteEntity, Set<String> lemmas) {
        List<SearchData> data = new ArrayList<>();
        if (!siteEntity.getStatus().equals(Status.INDEXED)) return data;
        int pageCount = pageRepository.countAllBySite(siteEntity).get();

        List<LemmaEntity> lemmaEntityList = getLemmaEntities(siteEntity, lemmas, pageCount);
        if (lemmaEntityList.size() == 0) return data;
        List<PageEntity> pages = new ArrayList<>();
        List<PageEntity> pageEntityList = new ArrayList<>();
        for (IndexEntity index : indexRepository.findByLemma(lemmaEntityList.get(0))) {
            pages.add(index.getPage());
            pageEntityList.add(index.getPage());
        }

        for (LemmaEntity l : lemmaEntityList) {
            for (PageEntity page : pages) {
                if (!indexRepository.existsByPageAndLemma(page, l)) pageEntityList.remove(page);
                if (pageEntityList.size() == 0) break;
            }
            if (pageEntityList.size() == 0) break;
        }

        if (pageEntityList.size() == 0) return data;
        return collectData(siteEntity, data, lemmaEntityList, pageEntityList);
    }

    private List<LemmaEntity> getLemmaEntities(SiteEntity siteEntity, Set<String> lemmas, int pageCount) {
        List<LemmaEntity> lemmaEntityList = new ArrayList<>();
        for (String lemma : lemmas) {
            LemmaEntity lemmaEntity = lemmaRepository.findByLemmaAndSite(lemma, siteEntity);
            if (lemmaEntity == null) return lemmaEntityList;
            if ((lemmaEntity.getFrequency() * 1.0) / pageCount < 0.65)
                lemmaEntityList.add(lemmaEntity);
        }

        if (lemmaEntityList.size() == 0) {
            return lemmaEntityList;
        }

        lemmaEntityList.sort((l1, l2) -> {
            if (l1.getFrequency() < l2.getFrequency()) return 1;
            return 0;
        });
        return lemmaEntityList;
    }

    private List<SearchData> collectData(SiteEntity siteEntity, List<SearchData> data, List<LemmaEntity> lemmaEntityList, List<PageEntity> pageEntityList) {
        int absRelevance;
        int maxAbsRelevance = 0;
        for (PageEntity page : pageEntityList) {
            absRelevance = 0;
            for (LemmaEntity lemma : lemmaEntityList) {
                absRelevance += indexRepository.findByPageAndLemma(page, lemma).getRank();
            }
            maxAbsRelevance = Integer.max(maxAbsRelevance, absRelevance);
        }

        for (PageEntity page : pageEntityList) {
            absRelevance = 0;
            for (LemmaEntity lemma : lemmaEntityList) {
                if (indexRepository.existsByPageAndLemma(page, lemma))
                    absRelevance += indexRepository.findByPageAndLemma(page, lemma).getRank();
            }
            double relevance = absRelevance * 1.0 / maxAbsRelevance;
            SearchData searchData = collectPageSearchData(siteEntity, page, lemmaEntityList, relevance);
            data.add(searchData);
        }
        return data;
    }

    private ResponseEntity returnResponse(String error, HttpStatus status) {
        ErrorResponse response = new ErrorResponse();
        response.setResult(false);
        response.setError(error);
        return ResponseEntity.status(status).body(response);
    }
}
