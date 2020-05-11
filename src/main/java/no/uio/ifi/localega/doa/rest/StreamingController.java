package no.uio.ifi.localega.doa.rest;

import io.minio.MinioClient;
import io.minio.errors.*;
import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.crypt4gh.pojo.header.DataEditList;
import no.uio.ifi.crypt4gh.pojo.header.Header;
import no.uio.ifi.crypt4gh.pojo.header.HeaderPacket;
import no.uio.ifi.crypt4gh.pojo.header.X25519ChaCha20IETFPoly1305HeaderPacket;
import no.uio.ifi.crypt4gh.stream.Crypt4GHInputStream;
import no.uio.ifi.crypt4gh.util.Crypt4GHUtils;
import no.uio.ifi.crypt4gh.util.KeyUtils;
import no.uio.ifi.localega.doa.aspects.AAIAspect;
import no.uio.ifi.localega.doa.dto.DestinationFormat;
import no.uio.ifi.localega.doa.model.LEGADataset;
import no.uio.ifi.localega.doa.model.LEGAFile;
import no.uio.ifi.localega.doa.repositories.DatasetRepository;
import no.uio.ifi.localega.doa.repositories.FileRepository;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.xmlpull.v1.XmlPullParserException;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.Collection;
import java.util.Set;

/**
 * REST controller incorporating streaming-related endpoints.
 */
@Slf4j
@RequestMapping("/files")
@RestController
public class StreamingController {

    @Autowired
    protected HttpServletRequest request;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @Value("${s3.bucket}")
    private String s3Bucket;

    @Value("${crypt4gh.private-key-path}")
    private String crypt4ghPrivateKeyPath;

    @Value("${crypt4gh.private-key-password-path}")
    private String crypt4ghPrivateKeyPasswordPath;

    @Value("${archive.path}")
    private String archivePath;

    /**
     * Streams the requested file.
     *
     * @param publicKey         Optional public key, if the re-encryption was requested.
     * @param fileId            ID of the file to stream.
     * @param destinationFormat Destination format.
     * @param startCoordinate   Start byte.
     * @param endCoordinate     End byte.
     * @return File-stream.
     * @throws Exception In case of some error.
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/{fileId}")
    public ResponseEntity<?> files(@RequestHeader(value = "Public-Key", required = false) String publicKey,
                                   @PathVariable(value = "fileId") String fileId,
                                   @RequestParam(value = "destinationFormat", required = false) String destinationFormat,
                                   @RequestParam(value = "startCoordinate", required = false) String startCoordinate,
                                   @RequestParam(value = "endCoordinate", required = false) String endCoordinate) throws Exception {
        Set<String> datasetIds = (Set<String>) request.getAttribute(AAIAspect.DATASETS);
        if (!checkPermissions(fileId, datasetIds)) {
            log.info("User doesn't have permissions to download requested file: {}", fileId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("User has permissions to download requested file: {}", fileId);
        LEGAFile file = fileRepository.findById(fileId).orElseThrow(() -> new RuntimeException(String.format("File with ID %s doesn't exist", fileId)));
        byte[] header = Hex.decodeHex(file.getHeader());
        InputStream bodyInputStream = getFileInputStream(file);
        String password = Files.readString(Path.of(crypt4ghPrivateKeyPasswordPath));
        PrivateKey privateKey = KeyUtils.getInstance().readPrivateKey(new File(crypt4ghPrivateKeyPath), password.toCharArray());
        if (DestinationFormat.CRYPT4GH.name().equalsIgnoreCase(destinationFormat)) {
            return getEncryptedResponse(file, header, bodyInputStream, privateKey, startCoordinate, endCoordinate, publicKey);
        } else {
            return getPlaintextResponse(file, header, bodyInputStream, privateKey, startCoordinate, endCoordinate);
        }
    }

    private ResponseEntity<?> getPlaintextResponse(LEGAFile file, byte[] header, InputStream bodyInputStream, PrivateKey privateKey, String startCoordinate, String endCoordinate) throws IOException, GeneralSecurityException {
        ByteArrayInputStream headerInputStream = new ByteArrayInputStream(header);
        SequenceInputStream sequenceInputStream = new SequenceInputStream(headerInputStream, bodyInputStream);
        Crypt4GHInputStream crypt4GHInputStream;
        if (!StringUtils.isEmpty(startCoordinate) && !StringUtils.isEmpty(endCoordinate)) {
            DataEditList dataEditList = new DataEditList(new long[]{Long.parseLong(startCoordinate), Long.parseLong(endCoordinate)});
            crypt4GHInputStream = new Crypt4GHInputStream(sequenceInputStream, dataEditList, privateKey);
        } else {
            crypt4GHInputStream = new Crypt4GHInputStream(sequenceInputStream, privateKey);
        }
        return ResponseEntity.ok().headers(getResponseHeaders(file, false)).body(new InputStreamResource(crypt4GHInputStream));
    }

    private ResponseEntity<?> getEncryptedResponse(LEGAFile file, byte[] header, InputStream bodyInputStream, PrivateKey privateKey, String startCoordinate, String endCoordinate, String publicKey) throws GeneralSecurityException, IOException {
        PublicKey recipientPublicKey = KeyUtils.getInstance().readPublicKey(publicKey);
        Header newHeader = Crypt4GHUtils.getInstance().setRecipient(header, privateKey, recipientPublicKey);
        if (!StringUtils.isEmpty(startCoordinate) && !StringUtils.isEmpty(endCoordinate)) {
            DataEditList dataEditList = new DataEditList(new long[]{Long.parseLong(startCoordinate), Long.parseLong(endCoordinate)});
            HeaderPacket dataEditListHeaderPacket = new X25519ChaCha20IETFPoly1305HeaderPacket(dataEditList, privateKey, recipientPublicKey);
            newHeader.getHeaderPackets().add(dataEditListHeaderPacket);
        }
        ByteArrayInputStream headerInputStream = new ByteArrayInputStream(newHeader.serialize());
        SequenceInputStream sequenceInputStream = new SequenceInputStream(headerInputStream, bodyInputStream);
        return ResponseEntity.ok().headers(getResponseHeaders(file, true)).body(new InputStreamResource(sequenceInputStream));
    }

    private HttpHeaders getResponseHeaders(LEGAFile file, boolean encrypted) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String fileName = file.getFileName();
        if (encrypted) {
            fileName += ".enc";
        }
        responseHeaders.setContentDisposition(ContentDisposition.builder("attachment").filename(fileName).build());
        return responseHeaders;
    }

    private InputStream getFileInputStream(LEGAFile file) throws IOException, InvalidKeyException, NoSuchAlgorithmException, InsufficientDataException, InvalidArgumentException, InvalidResponseException, InternalException, NoResponseException, InvalidBucketNameException, XmlPullParserException, ErrorResponseException {
        String filePath = file.getFilePath();
        try { // S3
            BigInteger s3FileId = new BigInteger(filePath);
            return minioClient.getObject(s3Bucket, s3FileId.toString());
        } catch (NumberFormatException e) { // filesystem
            String processedPath;
            if ("/".equalsIgnoreCase(archivePath)) {
                processedPath = filePath;
            } else if (archivePath.endsWith("/")) {
                String tempPath = archivePath.substring(0, archivePath.length() - 1);
                processedPath = tempPath + filePath;
            } else {
                processedPath = archivePath + filePath;
            }
            log.info("Archive path is: {}", processedPath);
            return Files.newInputStream(new File(processedPath).toPath());
        }
    }

    private boolean checkPermissions(String fileId, Set<String> datasetIds) {
        Collection<LEGADataset> datasets = datasetRepository.findByDatasetIdIn(datasetIds);
        for (LEGADataset dataset : datasets) {
            if (dataset.getFileId().equalsIgnoreCase(fileId)) {
                return true;
            }
        }
        return false;
    }

}
