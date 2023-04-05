package searchengine.dto.seach;

import lombok.Data;

import java.util.List;

@Data
public class SearchResponse {
    private boolean result;
    private int count = 0;
    private List<SearchData> data;
}
