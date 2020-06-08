package no.uio.ifi.localega.doa;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONArray;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.crypt4gh.stream.Crypt4GHInputStream;
import no.uio.ifi.crypt4gh.util.KeyUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Properties;

@Slf4j
@RunWith(JUnit4.class)
class LocalEGADOAApplicationTests {

    private static String validToken;
    private static String invalidToken;

    @SneakyThrows
    @BeforeAll
    public static void setup() {
        String url = String.format("jdbc:postgresql://%s:%s/%s", "localhost", "5432", "lega");
        Properties props = new Properties();
        props.setProperty("user", "lega_in");
        props.setProperty("password", "password");
        props.setProperty("ssl", "true");
        props.setProperty("application_name", "LocalEGA");
        props.setProperty("sslmode", "verify-full");
        props.setProperty("sslrootcert", new File("test/rootCA.pem").getAbsolutePath());
        props.setProperty("sslcert", new File("test/localhost-client.pem").getAbsolutePath());
        props.setProperty("sslkey", new File("test/localhost-client-key.der").getAbsolutePath());
        Connection connection = DriverManager.getConnection(url, props);
        PreparedStatement file = connection.prepareStatement("SELECT local_ega.insert_file('body.enc','requester@elixir-europe.org');");
        file.executeQuery();
        PreparedStatement header = connection.prepareStatement("UPDATE local_ega.files SET header = '637279707434676801000000010000006c00000000000000aa7ad1bb4f93bf5e4fb3bc28a95bc4d80bf2fd8075e69eb2ee15e0a4f08f1d78ab98c8fd9b50e675f71311936e8d0c6f73538962b836355d5d4371a12eae46addb43518b5236fb9554249710a473026f34b264a61d2ba52ed11abc1efa1d3478fa40a710' WHERE id = 1;");
        try {
            header.executeQuery();
        } catch (Exception e) {
            log.info(e.getMessage());
        }
        PreparedStatement finalize = connection.prepareStatement("UPDATE local_ega.files SET archive_path = 'test/body.enc', status = 'READY', stable_id = 'EGAF00000000014' WHERE id = 1;");
        try {
            finalize.executeQuery();
        } catch (Exception e) {
            log.info(e.getMessage());
        }
        connection.close();
        props.setProperty("user", "lega_out");
        connection = DriverManager.getConnection(url, props);
        PreparedStatement dataset = connection.prepareStatement("INSERT INTO local_ega_ebi.filedataset(file_id, dataset_stable_id) values(1, 'EGAD00010000919');");
        try {
            dataset.executeQuery();
        } catch (Exception e) {
            log.info(e.getMessage());
        }
        connection.close();

        JSONArray tokens = Unirest.get("http://localhost:8000/tokens").asJson().getBody().getArray();
        validToken = tokens.getString(0);
        invalidToken = tokens.getString(1);
    }

    @Test
    void testMetadataDatasetsNoToken() {
        int status = Unirest.get("http://localhost:8080/metadata/datasets").asJson().getStatus();
        Assert.assertEquals(HttpStatus.UNAUTHORIZED.value(), status);
    }

    @Test
    void testMetadataDatasetsInvalidToken() {
        int status = Unirest.get("http://localhost:8080/metadata/datasets").header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken).asJson().getStatus();
        Assert.assertEquals(HttpStatus.UNAUTHORIZED.value(), status);
    }

    @Test
    void testMetadataDatasetsValidToken() {
        HttpResponse<JsonNode> response = Unirest.get("http://localhost:8080/metadata/datasets").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken).asJson();
        int status = response.getStatus();
        Assert.assertEquals(HttpStatus.OK.value(), status);
        JSONArray datasets = response.getBody().getArray();
        Assert.assertEquals(1, datasets.length());
        Assert.assertEquals("EGAD00010000919", datasets.getString(0));
    }

    @Test
    void testMetadataFilesNoToken() {
        int status = Unirest.get("http://localhost:8080/metadata/datasets/EGAD00010000919/files").asJson().getStatus();
        Assert.assertEquals(HttpStatus.UNAUTHORIZED.value(), status);
    }

    @Test
    void testMetadataFilesInvalidToken() {
        int status = Unirest.get("http://localhost:8080/metadata/datasets/EGAD00010000919/files").header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken).asJson().getStatus();
        Assert.assertEquals(HttpStatus.UNAUTHORIZED.value(), status);
    }

    @Test
    void testMetadataFilesValidTokenInvalidDataset() {
        HttpResponse<JsonNode> response = Unirest.get("http://localhost:8080/metadata/datasets/EGAD00010000920/files").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken).asJson();
        int status = response.getStatus();
        Assert.assertEquals(HttpStatus.FORBIDDEN.value(), status);
    }

    @Test
    void testMetadataFilesValidTokenValidDataset() {
        HttpResponse<JsonNode> response = Unirest.get("http://localhost:8080/metadata/datasets/EGAD00010000919/files").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken).asJson();
        int status = response.getStatus();
        Assert.assertEquals(HttpStatus.OK.value(), status);
        Assert.assertEquals("[{\"fileId\":\"EGAF00000000014\",\"datasetId\":\"EGAD00010000919\",\"displayFileName\":\"body.enc\",\"fileName\":\"test/body.enc\",\"fileStatus\":\"READY\"}]", response.getBody().toString());
    }

    @Test
    void testStreamingNoToken() {
        int status = Unirest.get("http://localhost:8080/files/EGAF00000000014").asJson().getStatus();
        Assert.assertEquals(HttpStatus.UNAUTHORIZED.value(), status);
    }

    @Test
    void testStreamingInvalidToken() {
        int status = Unirest.get("http://localhost:8080/files/EGAF00000000014").header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken).asJson().getStatus();
        Assert.assertEquals(HttpStatus.UNAUTHORIZED.value(), status);
    }

    @Test
    void testStreamingValidTokenInvalidFile() {
        HttpResponse<JsonNode> response = Unirest.get("http://localhost:8080/files/EGAF00000000015").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken).asJson();
        int status = response.getStatus();
        Assert.assertEquals(HttpStatus.FORBIDDEN.value(), status);
    }

    @Test
    void testStreamingValidTokenValidFileFullPlain() {
        HttpResponse<byte[]> response = Unirest.get("http://localhost:8080/files/EGAF00000000014").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken).asBytes();
        int status = response.getStatus();
        Assert.assertEquals(HttpStatus.OK.value(), status);
        Assert.assertEquals("2aef808fb42fa7b1ba76cb16644773f9902a3fdc2569e8fdc049f38280c4577e", DigestUtils.sha256Hex(response.getBody()));
    }

    @Test
    void testStreamingValidTokenValidFileRangePlain() {
        HttpResponse<byte[]> response = Unirest.get("http://localhost:8080/files/EGAF00000000014?startCoordinate=100&endCoordinate=200").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken).asBytes();
        int status = response.getStatus();
        Assert.assertEquals(HttpStatus.OK.value(), status);
        Assert.assertEquals("09fbeae7cce2cd410b471b0a1a265fb53dc54c66c4c7c3111b8b9b95ac0e956f", DigestUtils.sha256Hex(response.getBody()));
    }

    @SneakyThrows
    @Test
    void testStreamingValidTokenValidFileFullEncrypted() {
        String publicKey = Files.readString(new File("test/crypt4gh.pub.pem").toPath());
        PrivateKey privateKey = KeyUtils.getInstance().readPrivateKey(new File("test/crypt4gh.sec.pem"), "password".toCharArray());
        HttpResponse<byte[]> response = Unirest.get("http://localhost:8080/files/EGAF00000000014?destinationFormat=crypt4gh").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken).header("Public-Key", publicKey).asBytes();
        int status = response.getStatus();
        Assert.assertEquals(HttpStatus.OK.value(), status);
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(response.getBody());
             Crypt4GHInputStream crypt4GHInputStream = new Crypt4GHInputStream(byteArrayInputStream, privateKey)) {
            byte[] bytes = IOUtils.toByteArray(crypt4GHInputStream);
            Assert.assertEquals("2aef808fb42fa7b1ba76cb16644773f9902a3fdc2569e8fdc049f38280c4577e", DigestUtils.sha256Hex(bytes));
        }
    }

    @SneakyThrows
    @Test
    void testStreamingValidTokenValidFileRangeEncrypted() {
        String publicKey = Files.readString(new File("test/crypt4gh.pub.pem").toPath());
        PrivateKey privateKey = KeyUtils.getInstance().readPrivateKey(new File("test/crypt4gh.sec.pem"), "password".toCharArray());
        HttpResponse<byte[]> response = Unirest.get("http://localhost:8080/files/EGAF00000000014?startCoordinate=100&endCoordinate=200&destinationFormat=crypt4gh").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken).header("Public-Key", publicKey).asBytes();
        int status = response.getStatus();
        Assert.assertEquals(HttpStatus.OK.value(), status);
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(response.getBody());
             Crypt4GHInputStream crypt4GHInputStream = new Crypt4GHInputStream(byteArrayInputStream, privateKey)) {
            byte[] bytes = IOUtils.toByteArray(crypt4GHInputStream);
            Assert.assertEquals("09fbeae7cce2cd410b471b0a1a265fb53dc54c66c4c7c3111b8b9b95ac0e956f", DigestUtils.sha256Hex(bytes));
        }
    }

}
