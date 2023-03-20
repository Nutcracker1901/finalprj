import searchengine.services.LemmaFinder;

import java.io.IOException;
import java.util.*;

public class LuceneTest {

    public static void main(String[] args) throws IOException {
        String text = "Повторное появление леопарда в Осетии позволяет предположить, что леопард постоянно обитает в некоторых районах Северного Кавказа.";
        LemmaFinder lemmaFinder = LemmaFinder.getInstance();
        Map<String, Integer> result = lemmaFinder.collectLemmas(text);

        System.out.println(result);
    }
}
