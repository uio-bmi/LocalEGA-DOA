package no.uio.ifi.localega.doa;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import io.minio.MinioClient;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import okhttp3.OkHttpClient;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.net.ssl.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.UUID;

@SpringBootApplication
public class LocalEGADOAApplication {

    public static final String BEGIN_PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----";
    public static final String END_PUBLIC_KEY = "-----END PUBLIC KEY-----";

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

    @Value("${jwt.public-key-path}")
    private String jwtPublicKeyPath;

    public static void main(String[] args) {
        SpringApplication.run(LocalEGADOAApplication.class, args);
    }

    @Bean
    public MinioClient minioClient() throws InvalidPortException, InvalidEndpointException, GeneralSecurityException {
        X509TrustManager trustManager = trustManagerForCertificates(IOUtils.toInputStream(s3RootCA, Charset.defaultCharset()));
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{trustManager}, null);
        OkHttpClient okHttpClient = new OkHttpClient.Builder().sslSocketFactory(sslContext.getSocketFactory(), trustManager).build();
        return new MinioClient(s3Endpoint, s3Port, s3AccessKey, s3SecretKey, s3Region, s3Secure, okHttpClient);
    }

    @Bean
    public JWTVerifier jwtVerifier() throws InvalidKeySpecException, NoSuchAlgorithmException, IOException {
        return JWT.require(Algorithm.RSA256(getPublicKey(), null)).build();
    }

    private RSAPublicKey getPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        String jwtPublicKey = FileUtils.readFileToString(new File(jwtPublicKeyPath), Charset.defaultCharset());
        String encodedKey = jwtPublicKey
                .replace(BEGIN_PUBLIC_KEY, "")
                .replace(END_PUBLIC_KEY, "")
                .replace(System.lineSeparator(), "")
                .replace(" ", "")
                .trim();
        byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
        return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
    }

    private X509TrustManager trustManagerForCertificates(InputStream in) throws GeneralSecurityException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(in);
        if (certificates.isEmpty()) {
            throw new IllegalArgumentException("expected non-empty set of trusted certificates");
        }

        // Put the certificates a key store.
        char[] password = UUID.randomUUID().toString().toCharArray(); // Any password will work.
        KeyStore keyStore = newEmptyKeyStore(password);
        int index = 0;
        for (Certificate certificate : certificates) {
            String certificateAlias = Integer.toString(index++);
            keyStore.setCertificateEntry(certificateAlias, certificate);
        }

        // Use it to build an X509 trust manager.
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
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
