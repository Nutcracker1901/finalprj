package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;

import java.util.List;

public interface IndexRepository extends JpaRepository<IndexEntity, Long> {
    boolean existsByPageAndLemma(PageEntity page, LemmaEntity lemma);
    IndexEntity findByPageAndLemma(PageEntity page, LemmaEntity lemma);
    List<IndexEntity> findByLemma(LemmaEntity lemma);
    void deleteAllByPage(PageEntity page);
    List<IndexEntity> findByPage(PageEntity page);
}
