package searchengine.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import javax.persistence.EntityManager;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    void deletePageEntitiesBySite(SiteEntity site);

    void deleteAllBySiteId(int siteId);
}
