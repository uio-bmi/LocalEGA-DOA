package no.uio.ifi.localega.doa.rest;

import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.minio.MinioClient;
import io.minio.errors.*;
import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.crypt4gh.pojo.header.Header;
import no.uio.ifi.crypt4gh.stream.Crypt4GHInputStream;
import no.uio.ifi.crypt4gh.util.Crypt4GHUtils;
import no.uio.ifi.crypt4gh.util.KeyUtils;
import no.uio.ifi.localega.doa.model.LEGADataset;
import no.uio.ifi.localega.doa.model.LEGAFile;
import no.uio.ifi.localega.doa.repositories.DatasetRepository;
import no.uio.ifi.localega.doa.repositories.FileRepository;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.xmlpull.v1.XmlPullParserException;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class LocalEGADOAController {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private JWTVerifier jwtVerifier;

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

    @RequestMapping("/files/{fileId}")
    @ResponseBody
    public ResponseEntity<?> files(@RequestHeader("Authorization") String token,
                                   @RequestHeader(value = "Public-Key", required = false) String publicKey,
                                   @PathVariable(value = "fileId") String fileId) throws Exception {
        if (StringUtils.isEmpty(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        DecodedJWT decodedJWT = jwtVerifier.verify(token.replace("Bearer ", ""));
        String subject = decodedJWT.getSubject();
        log.info("User {} is authenticated and attempting to download the file: {}", subject, fileId);
        Set<String> datasetIds = Arrays.stream(decodedJWT.getClaim("authorities").asArray(String.class)).collect(Collectors.toSet());
        if (!checkPermissions(fileId, datasetIds)) {
            log.info("User doesn't have permissions to access this file, abort");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("User has permissions to access the file, initializing download");
        LEGAFile file = fileRepository.findByFileId(fileId).orElseThrow(() -> new RuntimeException(String.format("File with ID %s doesn't exist", fileId)));
        byte[] header = Hex.decodeHex(file.getHeader());
        InputStream bodyInputStream = getFileInputStream(file);
        String password = FileUtils.readFileToString(new File(crypt4ghPrivateKeyPasswordPath), Charset.defaultCharset());
        PrivateKey privateKey = KeyUtils.getInstance().readPrivateKey(new File(crypt4ghPrivateKeyPath), password.toCharArray());
        if (StringUtils.isEmpty(publicKey)) {
            return getPlaintextResponse(file, header, bodyInputStream, privateKey);
        } else {
            return getEncryptedResponse(publicKey, file, header, bodyInputStream, privateKey);
        }
    }

    private ResponseEntity<?> getPlaintextResponse(LEGAFile file, byte[] header, InputStream bodyInputStream, PrivateKey privateKey) throws IOException, GeneralSecurityException {
        ByteArrayInputStream headerInputStream = new ByteArrayInputStream(header);
        SequenceInputStream sequenceInputStream = new SequenceInputStream(headerInputStream, bodyInputStream);
        Crypt4GHInputStream crypt4GHInputStream = new Crypt4GHInputStream(sequenceInputStream, privateKey);
        return ResponseEntity.ok().headers(getResponseHeaders(file, false)).body(new InputStreamResource(crypt4GHInputStream));
    }

    private ResponseEntity<?> getEncryptedResponse(String publicKey, LEGAFile file, byte[] header, InputStream bodyInputStream, PrivateKey privateKey) throws GeneralSecurityException, IOException {
        PublicKey recipientPublicKey = KeyUtils.getInstance().readPublicKey(publicKey);
        Header newHeader = Crypt4GHUtils.getInstance().setRecipient(header, privateKey, recipientPublicKey);
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
        if (StringUtils.isEmpty(file.getFilePath())) { // S3
            return minioClient.getObject(s3Bucket, file.getId().toString());
        } else { // filesystem
            return Files.newInputStream(new File(file.getFilePath()).toPath());
        }
    }

    private boolean checkPermissions(String fileId, Set<String> datasetIds) {
        for (String datasetId : datasetIds) {
            Collection<LEGADataset> datasets = datasetRepository.findByDatasetId(datasetId);
            for (LEGADataset dataset : datasets) {
                if (dataset.getFileId().equalsIgnoreCase(fileId)) {
                    return true;
                }
            }
        }
        return false;
    }

}
