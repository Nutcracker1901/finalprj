package searchengine.lemmatizer;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;

public class LemmaFinder {
    private final LuceneMorphology luceneMorphologyRussian;
    private final LuceneMorphology luceneMorphologyEnglish;
    private static final String WORD_TYPE_REGEX_RUSSIAN = "\\W\\w&&[^а-яА-Я\\s]";
    private static final String WORD_TYPE_REGEX_ENGLISH = "\\W\\w&&[^a-zA-Z\\s]";
    private static final String[] russianParticlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
    private static final String[] englishParticlesNames = new String[]{"ARTICLE", "CONJ", "INTERJ", "PART", "PREP"};

    public static LemmaFinder getInstance() throws IOException {
        LuceneMorphology morphologyRus = new RussianLuceneMorphology();
        LuceneMorphology morphologyEng = new EnglishLuceneMorphology();
        return new LemmaFinder(morphologyRus, morphologyEng);
    }

    private LemmaFinder(LuceneMorphology luceneMorphologyRussian, LuceneMorphology luceneMorphologyEnglish) {
        this.luceneMorphologyRussian = luceneMorphologyRussian;
        this.luceneMorphologyEnglish = luceneMorphologyEnglish;
    }

    private LemmaFinder() {
        throw new RuntimeException("Disallow construct");
    }

    public Map<String, Integer> collectLemmas(String text) {
        String[] wordsRus = arrayContainsRussianWords(text);
        String[] wordsEng = arrayContainsEnglishWords(text);

        HashMap<String, Integer> lemmas = new HashMap<>();

        addLemmas(wordsRus, lemmas, luceneMorphologyRussian, Language.RUS);

        addLemmas(wordsEng, lemmas, luceneMorphologyEnglish, Language.ENG);

        return lemmas;
    }

    private void addLemmas(String[] words, HashMap<String, Integer> lemmas, LuceneMorphology luceneMorphology, Language lang) {
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            if (anyWordBaseBelongToParticle(wordBaseForms, lang)) {
                continue;
            }

            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) {
                continue;
            }
            String normalWord = normalForms.get(0);

            if (lemmas.containsKey(normalWord)) lemmas.put(normalWord, lemmas.get(normalWord) + 1);
            else lemmas.put(normalWord, 1);
        }
    }

    /**
     * @param text текст из которого собираем все леммы
     * @return набор уникальных лемм найденных в тексте
     */
    public Set<String> getLemmaSet(String text) {
        String[] textArrayRus = arrayContainsRussianWords(text);
        String[] textArrayEng = arrayContainsEnglishWords(text);
        Set<String> lemmaSet = new HashSet<>();
        addLemmasToSet(textArrayRus, lemmaSet, luceneMorphologyRussian, Language.RUS);
        addLemmasToSet(textArrayEng, lemmaSet, luceneMorphologyEnglish, Language.ENG);
        return lemmaSet;
    }

    private void addLemmasToSet(String[] textArray, Set<String> lemmaSet, LuceneMorphology luceneMorphology, Language lang) {
        for (String word : textArray) {
            if (!word.isEmpty() && isCorrectWordForm(word, luceneMorphology)) {
                List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
                if (anyWordBaseBelongToParticle(wordBaseForms, lang)) {
                    continue;
                }
                lemmaSet.addAll(luceneMorphology.getNormalForms(word));
            }
        }
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms, Language lang) {
        if (lang.equals(Language.RUS))
            return wordBaseForms.stream().anyMatch(this::hasParticlePropertyRus);
        return wordBaseForms.stream().anyMatch(this::hasParticlePropertyEng);
    }

    private boolean hasParticlePropertyRus(String wordBase) {
        for (String property : russianParticlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasParticlePropertyEng(String wordBase) {
        for (String property : englishParticlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private String[] arrayContainsRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-я\\s])", " ")
                .trim()
                .split("\\s+");
    }

    private String[] arrayContainsEnglishWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^a-z\\s])", " ")
                .trim()
                .split("\\s+");
    }

    private boolean isCorrectWordForm(String word, LuceneMorphology luceneMorphology) {
        List<String> wordInfo;
        wordInfo = luceneMorphology.getMorphInfo(word);
        for (String morphInfo : wordInfo) {
            if (morphInfo.matches(WORD_TYPE_REGEX_RUSSIAN) || morphInfo.matches(WORD_TYPE_REGEX_ENGLISH)) {
                return false;
            }
        }
        return true;
    }
}
