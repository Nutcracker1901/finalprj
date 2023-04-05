package searchengine.dto.seach;

import lombok.Data;

@Data
public class SearchData {
    String site;
    String siteName;
    String uri;
    String title;
    String snippet;
    double relevance;
}
