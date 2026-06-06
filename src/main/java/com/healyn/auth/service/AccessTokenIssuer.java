package com.healyn.auth.service;

import com.healyn.auth.config.AuthProperties;
import com.healyn.auth.domain.Account;
import com.healyn.common.id.UuidV7;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

@Component
public class AccessTokenIssuer {

    private final AuthProperties.Jwt props;
    private final JwtKeyProvider keys;

    public AccessTokenIssuer(AuthProperties.Jwt props, JwtKeyProvider keys) {
        this.props = props;
        this.keys = keys;
    }

    public Issued issue(Account account) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.accessTokenTtlSeconds());
        String jti = UuidV7.generate().toString();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(props.issuer())
                .audience(props.audience())
                .subject(account.getId().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .jwtID(jti)
                .claim("role", account.getRole().name())
                .claim("ver", 1)
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keys.keyId()).build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(keys.privateKey()));
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign access token", e);
        }
        return new Issued(jwt.serialize(), jti, exp);
    }

    public record Issued(String token, String jti, Instant expiresAt) {}
}
