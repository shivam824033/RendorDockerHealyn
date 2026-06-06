package com.healyn.auth.service;

import com.healyn.auth.config.AuthProperties;
import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.common.id.UuidV7;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessTokenIssuerTest {

    private static RSAPrivateKey privateKey;
    private static RSAPublicKey publicKey;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        privateKey = (RSAPrivateKey) kp.getPrivate();
        publicKey = (RSAPublicKey) kp.getPublic();
    }

    @Test
    void issued_token_verifies_with_public_key_and_carries_required_claims() throws Exception {
        AuthProperties.Jwt props = new AuthProperties.Jwt(
                "healyn-test", "healyn-mobile", 900, 30, null, null);
        JwtKeyProvider keys = mock(JwtKeyProvider.class);
        when(keys.privateKey()).thenReturn(privateKey);
        when(keys.publicKey()).thenReturn(publicKey);
        when(keys.keyId()).thenReturn("test-kid");

        Account account = new Account(UuidV7.generate(), "u@x.com", null,
                "hash", new byte[]{1, 2}, AccountRole.ROLE_ACCOUNT);

        AccessTokenIssuer issuer = new AccessTokenIssuer(props, keys);
        AccessTokenIssuer.Issued issued = issuer.issue(account);

        SignedJWT jwt = SignedJWT.parse(issued.token());
        assertThat(jwt.verify(new RSASSAVerifier(publicKey))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("healyn-test");
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("healyn-mobile");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(account.getId().toString());
        assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("ROLE_ACCOUNT");
        assertThat(jwt.getJWTClaimsSet().getIntegerClaim("ver")).isEqualTo(1);
        assertThat(jwt.getJWTClaimsSet().getJWTID()).isEqualTo(issued.jti());
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("test-kid");
    }
}
