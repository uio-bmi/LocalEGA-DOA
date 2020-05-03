package no.uio.ifi.localega.doa;

import io.minio.MinioClient;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Boot main file containing the application entry-point and all necessary Spring beans configuration.
 */
@Slf4j
@SpringBootApplication
public class LocalEGADOAApplication {

    @Value("${s3.endpoint}")
    private String s3Endpoint;

    @Value("${s3.port}")
    private int s3Port;

    @Value("${s3.access-key}")
    private String s3AccessKey;

    @Value("${s3.secret-key}")
    private String s3SecretKey;

    @Value("${s3.region}")
    private String s3Region;

    @Value("${s3.secure}")
    private boolean s3Secure;

    @Value("${s3.root-ca}")
    private String s3RootCA;

    /**
     * Spring boot entry-point.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        SpringApplication.run(LocalEGADOAApplication.class, args);
    }

    /**
     * Minio Client Spring bean.
     *
     * @return <code>MinioClient</code>
     * @throws InvalidPortException     In case of invalid port.
     * @throws InvalidEndpointException In case of invalid endpoint.
     * @throws GeneralSecurityException In case of SSL/TLS related errors.
     */
    @Bean
    public MinioClient minioClient() throws InvalidPortException, InvalidEndpointException, GeneralSecurityException {
        Optional<OkHttpClient> optionalOkHttpClient = buildOkHttpClient();
        return new MinioClient(s3Endpoint, s3Port, s3AccessKey, s3SecretKey, s3Region, s3Secure, optionalOkHttpClient.orElse(null));
    }

    private Optional<OkHttpClient> buildOkHttpClient() throws GeneralSecurityException {
        try {
            X509TrustManager trustManager = trustManagerForCertificates(Files.newInputStream(Path.of(s3RootCA)));
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, null);
            return Optional.of(new OkHttpClient.Builder().sslSocketFactory(sslContext.getSocketFactory(), trustManager).build());
        } catch (IOException e) {
            log.warn("S3 Root CA file {} does not exist, skipping...", s3RootCA);
            return Optional.empty();
        }
    }

    private X509TrustManager trustManagerForCertificates(InputStream in) throws GeneralSecurityException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(in);
        if (certificates.isEmpty()) {
            throw new IllegalArgumentException("Expected non-empty set of trusted certificates");
        }

        // put the certificates into a key store
        char[] password = UUID.randomUUID().toString().toCharArray(); // any password will do
        KeyStore keyStore = newEmptyKeyStore(password);
        for (Certificate certificate : certificates) {
            keyStore.setCertificateEntry(UUID.randomUUID().toString(), certificate);
        }

        // use it to build an X509 trust manager
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
            throw new IllegalStateException("Unexpected default trust managers: " + Arrays.toString(trustManagers));
        }
        return (X509TrustManager) trustManagers[0];
    }

    private KeyStore newEmptyKeyStore(char[] password) throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, password);
            return keyStore;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

}
