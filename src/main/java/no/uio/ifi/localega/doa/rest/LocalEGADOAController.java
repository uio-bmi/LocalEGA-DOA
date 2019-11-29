package no.uio.ifi.localega.doa.rest;

import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.interfaces.DecodedJWT;
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
import no.uio.ifi.localega.doa.dto.DestinationFormat;
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
import java.math.BigInteger;
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

    @GetMapping("/files/{fileId}")
    public ResponseEntity<?> files(@RequestHeader(value = "Authorization") String token,
                                   @RequestHeader(value = "Public-Key", required = false) String publicKey,
                                   @PathVariable(value = "fileId") String fileId,
                                   @RequestParam(value = "destinationFormat", required = false) String destinationFormat,
                                   @RequestParam(value = "startCoordinate", required = false) String startCoordinate,
                                   @RequestParam(value = "endCoordinate", required = false) String endCoordinate) throws Exception {
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
            return Files.newInputStream(new File(filePath).toPath());
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
