package no.uio.ifi.localega.doa.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Model-POJO for Hibernate/Spring Data, describes LocalEGA file.
 */
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
@Entity
@Immutable
@Table(schema = "local_ega_ebi", name = "file")
@Data
@EqualsAndHashCode(of = {"fileId"})
@ToString
@RequiredArgsConstructor
public class LEGAFile {

    @Id
    @Column(name = "file_id", insertable = false, updatable = false, length = 128)
    private String fileId;

    @Column(name = "file_name", insertable = false, updatable = false, length = 256)
    private String fileName;

    @Column(name = "file_path", insertable = false, updatable = false, length = 256)
    private String filePath;

    @Column(name = "display_file_name", insertable = false, updatable = false, length = 128)
    private String displayFileName;

    @Column(name = "file_size", insertable = false, updatable = false)
    private Long fileSize;

    @Column(insertable = false, updatable = false, length = 128)
    private String checksum;

    @Column(name = "checksum_type", insertable = false, updatable = false, length = 12)
    private String checksumType;

    @Column(name = "unencrypted_checksum", insertable = false, updatable = false, length = 128)
    private String unencryptedChecksum;

    @Column(name = "unencrypted_checksum_type", insertable = false, updatable = false, length = 12)
    private String unencryptedChecksumType;

    @Column(name = "file_status", insertable = false, updatable = false, length = 13)
    private String fileStatus;

    @Column(insertable = false, updatable = false)
    private String header;

}
