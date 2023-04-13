package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {
    LemmaEntity findByLemmaAndSite(String lemma, SiteEntity site);

    Boolean existsByLemmaAndSite(String lemma, SiteEntity site);

    Optional<Integer> countAllBySite(SiteEntity site);
}
