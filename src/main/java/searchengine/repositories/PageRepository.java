package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Long> {
    boolean existsBySiteAndPath(SiteEntity site, String newPath);
    Optional<Integer> countAllBySite(SiteEntity site);
    List<PageEntity> findAllBySite(SiteEntity site);
    void deleteAllBySite(SiteEntity site);
    PageEntity findBySiteAndPath(SiteEntity site, String path);
}
