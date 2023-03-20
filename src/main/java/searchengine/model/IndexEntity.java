package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "`index`")
@Getter
@Setter
public class IndexEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private PageEntity page;
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private LemmaEntity lemma;
    @Column(name = "`rank`", columnDefinition = "FLOAT", nullable = false)
    private float rank;
}
