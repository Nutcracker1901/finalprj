package searchengine.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Long> {
    boolean existsBySiteAndPath(SiteEntity site, String newPath);
    int countAllBySite(SiteEntity site);
    List<PageEntity> findAllBySite(SiteEntity site);
    void deleteAllBySite(SiteEntity site);
    PageEntity findBySiteAndPath(SiteEntity site, String path);
}
