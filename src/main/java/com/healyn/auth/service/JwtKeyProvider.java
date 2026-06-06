package com.healyn.auth.service;

import com.healyn.auth.config.AuthProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;

@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);
    private static final Set<String> EPHEMERAL_PROFILES = Set.of("local", "test");

    private final AuthProperties.Jwt props;
    private final Environment env;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private String keyId;

    public JwtKeyProvider(AuthProperties.Jwt props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @PostConstruct
    void init() {
        boolean privateConfigured = isSet(props.privateKeyPath());
        boolean publicConfigured = isSet(props.publicKeyPath());

        if (privateConfigured && publicConfigured) {
            this.privateKey = loadPrivate(props.privateKeyPath());
            this.publicKey = loadPublic(props.publicKeyPath());
            this.keyId = fingerprint(publicKey);
            return;
        }

        boolean ephemeralOk = false;
        for (String p : env.getActiveProfiles()) if (EPHEMERAL_PROFILES.contains(p)) ephemeralOk = true;
        if (!ephemeralOk) {
            throw new IllegalStateException(
                    "healyn.jwt.private-key-path / public-key-path must be configured outside local/test profiles");
        }

        log.warn("JWT keys not configured — generating ephemeral RSA-2048 keypair (active profile only).");
        KeyPair kp = generateEphemeral();
        this.privateKey = (RSAPrivateKey) kp.getPrivate();
        this.publicKey = (RSAPublicKey) kp.getPublic();
        this.keyId = fingerprint(publicKey);
    }

    public RSAPrivateKey privateKey() { return privateKey; }
    public RSAPublicKey publicKey() { return publicKey; }
    public String keyId() { return keyId; }

    private static boolean isSet(String s) { return s != null && !s.isBlank(); }

    private static RSAPrivateKey loadPrivate(String path) {
        try {
            byte[] der = pemBody(Files.readString(Path.of(path)));
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to load JWT private key from " + path, e);
        }
    }

    private static RSAPublicKey loadPublic(String path) {
        try {
            byte[] der = pemBody(Files.readString(Path.of(path)));
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(der));
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to load JWT public key from " + path, e);
        }
    }

    private static byte[] pemBody(String pem) {
        String body = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(body);
    }

    private static KeyPair generateEphemeral() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String fingerprint(RSAPublicKey key) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOf(digest, 8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
