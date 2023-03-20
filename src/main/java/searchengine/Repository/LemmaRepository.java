package searchengine.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    LemmaEntity findByLemmaAndSite(String lemma, SiteEntity site);
    Boolean existsByLemmaAndSite(String lemma, SiteEntity site);
    void deleteAllBySite(SiteEntity site);
    void deleteAllBySiteId(int siteId);
}
