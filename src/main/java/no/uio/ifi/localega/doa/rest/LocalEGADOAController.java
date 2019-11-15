package no.uio.ifi.localega.doa.rest;

import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.minio.MinioClient;
import io.minio.errors.*;
import no.uio.ifi.crypt4gh.stream.Crypt4GHInputStream;
import no.uio.ifi.crypt4gh.util.KeyUtils;
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

import java.io.*;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Value("${crypt4gh.secret-key}")
    private String crypt4ghSecretKey;

    @RequestMapping("/download/{fileId}")
    @ResponseBody
    public ResponseEntity<?> download(@RequestHeader("Authorization") String token,
                                      @RequestHeader(value = "PublicKey", required = false) String publicKey,
                                      @PathVariable(value = "fileId") String fileId,
                                      @RequestParam(value = "startByte", required = false, defaultValue = "0") long startByte,
                                      @RequestParam(value = "endByte", required = false, defaultValue = "0") long endByte) throws Exception {
        if (StringUtils.isEmpty(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        DecodedJWT decodedJWT = jwtVerifier.verify(token.replace("Bearer ", ""));
        Set<String> datasetIds = Arrays.stream(decodedJWT.getClaim("authorities").asArray(String.class)).collect(Collectors.toSet());
        if (!checkPermissions(fileId, datasetIds)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        LEGAFile file = fileRepository.findByFileId(fileId).orElseThrow(() -> new RuntimeException(String.format("File with ID %s doesn't exist", fileId)));
        byte[] header = Hex.decodeHex(file.getHeader());
        InputStream bodyInputStream = getFileInputStream(file);
        PrivateKey privateKey = KeyUtils.getInstance().readKey(crypt4ghSecretKey, PrivateKey.class);
        if (StringUtils.isEmpty(publicKey)) {
            return getPlaintextResponse(file, header, bodyInputStream, privateKey);
        } else {
            ByteArrayInputStream headerInputStream = new ByteArrayInputStream(header);
            SequenceInputStream sequenceInputStream = new SequenceInputStream(headerInputStream, bodyInputStream);
            Crypt4GHInputStream crypt4GHInputStream = new Crypt4GHInputStream(sequenceInputStream, privateKey);
            return ResponseEntity.ok().headers(getResponseHeaders(file)).build();
        }
    }

    private ResponseEntity<?> getPlaintextResponse(LEGAFile file, byte[] header, InputStream bodyInputStream, PrivateKey privateKey) throws IOException, GeneralSecurityException {
        ByteArrayInputStream headerInputStream = new ByteArrayInputStream(header);
        SequenceInputStream sequenceInputStream = new SequenceInputStream(headerInputStream, bodyInputStream);
        Crypt4GHInputStream crypt4GHInputStream = new Crypt4GHInputStream(sequenceInputStream, privateKey);
        return ResponseEntity.ok().headers(getResponseHeaders(file)).body(new InputStreamResource(crypt4GHInputStream));
    }

    private HttpHeaders getResponseHeaders(LEGAFile file) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        responseHeaders.setContentDisposition(ContentDisposition.builder("attachment").filename(file.getFileName()).build());
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
