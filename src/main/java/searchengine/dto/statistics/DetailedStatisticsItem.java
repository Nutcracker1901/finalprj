package searchengine.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetailedStatisticsItem {
    private String url;
    private String name;
    private String status;
    private Date statusTime;
    private String error;
    private int pages;
    private int lemmas;
}
