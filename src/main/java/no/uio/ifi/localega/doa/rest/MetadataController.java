package no.uio.ifi.localega.doa.rest;

import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.localega.doa.dto.File;
import no.uio.ifi.localega.doa.model.LEGADataset;
import no.uio.ifi.localega.doa.repositories.DatasetRepository;
import no.uio.ifi.localega.doa.repositories.FileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequestMapping("/metadata")
@RestController
public class MetadataController {

    @Autowired
    private JWTVerifier jwtVerifier;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @GetMapping("/datasets")
    public ResponseEntity<?> datasets(@RequestHeader(value = "Authorization") String token) {
        DecodedJWT decodedJWT = jwtVerifier.verify(token.replace("Bearer ", ""));
        String subject = decodedJWT.getSubject();
        log.info("User {} is authenticated and is getting the list of datasets", subject);
        Set<String> datasetIds = Arrays.stream(decodedJWT.getClaim("authorities").asArray(String.class)).collect(Collectors.toSet());
        return ResponseEntity.ok(datasetIds);
    }

    @GetMapping("/datasets/{datasetId}/files")
    public ResponseEntity<?> files(@RequestHeader(value = "Authorization") String token,
                                   @PathVariable(value = "datasetId") String datasetId) {
        DecodedJWT decodedJWT = jwtVerifier.verify(token.replace("Bearer ", ""));
        String subject = decodedJWT.getSubject();
        log.info("User {} is authenticated and is attempting to list files of dataset {}", subject, datasetId);
        Set<String> datasetIds = Arrays.stream(decodedJWT.getClaim("authorities").asArray(String.class)).collect(Collectors.toSet());
        if (!datasetIds.contains(datasetId)) {
            log.info("User doesn't have permissions to access this dataset, abort");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("User has permissions to access this dataset, listing");
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
