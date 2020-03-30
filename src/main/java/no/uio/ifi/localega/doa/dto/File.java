package no.uio.ifi.localega.doa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class File {

    private String fileId;
    private String datasetId;
    private String displayFileName;
    private String fileName;
    private Long fileSize;
    private String unencryptedChecksum;
    private String unencryptedChecksumType;
    private String fileStatus;

}
