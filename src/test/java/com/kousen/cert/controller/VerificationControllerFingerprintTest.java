package com.kousen.cert.controller;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationControllerFingerprintTest {

    private static final Path TEST_KEYSTORE = Path.of("src/test/resources/keystores/test_keystore.p12");

    @Test
    void shouldReturnColonSeparatedSha256OfSigningCertificate() throws Exception {
        KeyStore ks = loadTestKeystore();

        // Independently computed expected value
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(ks.getCertificate("authorKey").getEncoded());
        StringBuilder expected = new StringBuilder();
        for (byte b : digest) {
            if (!expected.isEmpty()) {
                expected.append(':');
            }
            expected.append(String.format("%02X", b));
        }

        String fingerprint = VerificationController.fingerprintOf(ks);

        assertThat(fingerprint)
                .isEqualTo(expected.toString())
                .matches("^([0-9A-F]{2}:){31}[0-9A-F]{2}$");
    }

    @Test
    void shouldReportNotAvailableWhenAliasIsMissing() throws Exception {
        KeyStore empty = KeyStore.getInstance("PKCS12");
        empty.load(null, null);

        assertThat(VerificationController.fingerprintOf(empty)).isEqualTo(VerificationController.NOT_AVAILABLE);
    }

    @Test
    void shouldReportErrorWhenKeystoreIsUnusable() throws Exception {
        KeyStore uninitialized = KeyStore.getInstance("PKCS12");  // never loaded -> KeyStoreException

        assertThat(VerificationController.fingerprintOf(uninitialized)).isEqualTo(VerificationController.ERROR);
    }

    private static KeyStore loadTestKeystore() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(TEST_KEYSTORE)) {
            ks.load(in, "changeit".toCharArray());
        }
        return ks;
    }
}
