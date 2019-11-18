package no.uio.ifi.localega.doa;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import io.minio.MinioClient;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

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

    @Value("${jwt.public-key-path}")
    private String jwtPublicKeyPath;

    public static void main(String[] args) {
        SpringApplication.run(LocalEGADOAApplication.class, args);
    }

    @Bean
    public MinioClient minioClient() throws InvalidPortException, InvalidEndpointException {
        return new MinioClient(s3Endpoint, s3Port, s3AccessKey, s3SecretKey, s3Region, s3Secure);
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

}
