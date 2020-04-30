package no.uio.ifi.localega.doa.rest;

import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.localega.doa.aspects.AAIAspect;
import no.uio.ifi.localega.doa.dto.File;
import no.uio.ifi.localega.doa.model.LEGADataset;
import no.uio.ifi.localega.doa.repositories.DatasetRepository;
import no.uio.ifi.localega.doa.repositories.FileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST controller incorporating metadata-related endpoints.
 */
@Slf4j
@RequestMapping("/metadata")
@RestController
public class MetadataController {

    @Autowired
    protected HttpServletRequest request;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    /**
     * Lists datasets.
     *
     * @return List of datasets.
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/datasets")
    public ResponseEntity<?> datasets() {
        log.info("User has permissions to list datasets");
        Set<String> datasetIds = (Set<String>) request.getAttribute(AAIAspect.DATASETS);
        Collection<LEGADataset> datasets = datasetRepository.findByDatasetIdIn(datasetIds);
        return ResponseEntity.ok(datasets.stream().map(LEGADataset::getDatasetId).collect(Collectors.toSet()));
    }

    /**
     * Lists files in the dataset.
     *
     * @param datasetId Dataset ID.
     * @return List of files in the dataset.
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/datasets/{datasetId}/files")
    public ResponseEntity<?> files(@PathVariable(value = "datasetId") String datasetId) {
        Set<String> datasetIds = (Set<String>) request.getAttribute(AAIAspect.DATASETS);
        if (!datasetIds.contains(datasetId)) {
            log.info("User doesn't have permissions to list files in the requested dataset: {}", datasetId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("User has permissions to list files in the requested dataset: {}", datasetId);
        Collection<LEGADataset> datasets = datasetRepository.findByDatasetId(datasetId);
        List<File> files = datasets
                .stream()
                .map(LEGADataset::getFileId)
                .map(f -> fileRepository.findById(f))
                .flatMap(Optional::stream)
                .map(f -> {
                    File file = new File();
                    file.setFileId(f.getFileId());
                    file.setDatasetId(datasetId);
                    file.setDisplayFileName(f.getDisplayFileName());
                    file.setFileName(f.getFileName());
                    file.setFileSize(f.getFileSize());
                    file.setUnencryptedChecksum(f.getUnencryptedChecksum());
                    file.setUnencryptedChecksumType(f.getUnencryptedChecksumType());
                    file.setFileStatus(f.getFileStatus());
                    return file;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(files);
    }

}
