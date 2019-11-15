package no.uio.ifi.localega.doa.model;

import lombok.*;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.math.BigInteger;

@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
@Entity
@Table(name = "datasets")
@Data
@EqualsAndHashCode(of = {"id"})
@ToString
@RequiredArgsConstructor
@NoArgsConstructor
public class LEGADataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private BigInteger id;

    @NonNull
    @Column(name = "dataset_id", nullable = false)
    private String datasetId;

    @NonNull
    @Column(name = "file_id", nullable = false)
    private String fileId;

}
