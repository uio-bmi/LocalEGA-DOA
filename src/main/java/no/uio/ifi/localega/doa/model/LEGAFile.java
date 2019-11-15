package no.uio.ifi.localega.doa.model;

import lombok.*;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigInteger;

@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
@Entity
@Table(name = "files")
@Data
@EqualsAndHashCode(of = {"id"})
@ToString
@RequiredArgsConstructor
@NoArgsConstructor
public class LEGAFile {

    @Id
    @Column(name = "id", nullable = false)
    private BigInteger id;

    @NonNull
    @Column(name = "file_id", nullable = false)
    private String fileId;

    @Column(name = "file_path")
    private String filePath;

    @NonNull
    @Column(name = "file_name", nullable = false)
    private String fileName;

    @NonNull
    @Column(name = "header", nullable = false)
    private String header;

}
