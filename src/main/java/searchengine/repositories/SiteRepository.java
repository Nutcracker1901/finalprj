package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteEntity;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Long> {
    SiteEntity findByName(String name);
    SiteEntity findByUrl(String url);
    boolean existsByName(String name);
    boolean existsByUrl(String url);
}
