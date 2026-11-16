package com.kousen.cert.controller;

import com.kousen.cert.analytics.service.AnalyticsService;
import com.kousen.cert.service.KeyStoreProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

@Controller
public class VerificationController {

    private static final Logger logger = LoggerFactory.getLogger(VerificationController.class);
    static final String KEY_ALIAS = "authorKey";
    static final String NOT_AVAILABLE = "Certificate fingerprint not available";
    static final String ERROR = "Error generating certificate fingerprint";

    private final String certificateFingerprint;
    private final AnalyticsService analyticsService;

    public VerificationController(KeyStoreProvider keyStoreProvider, AnalyticsService analyticsService) {
        this.certificateFingerprint = fingerprintOf(keyStoreProvider.keyStore());
        this.analyticsService = analyticsService;
    }

    @GetMapping("/verify-certificate")
    public String verifyPage(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "book", required = false) String bookTitle,
            @RequestParam(name = "date", required = false) String issueDate,
            @RequestParam(name = "id", required = false) String certificateId,
            Model model,
            HttpServletRequest request) {

        logger.info("Verification request received for: name={}, book={}, date={}, id={}",
                name, bookTitle, issueDate, certificateId);

        model.addAttribute("name", name != null ? name : "Not specified");
        model.addAttribute("bookTitle", bookTitle != null ? bookTitle : "Not specified");
        model.addAttribute("issueDate", issueDate != null ? issueDate : "Not specified");
        model.addAttribute("certificateFingerprint", certificateFingerprint);

        if (certificateId != null && !certificateId.isEmpty()) {
            try {
                analyticsService.trackCertificateVerified(certificateId, request);
                logger.info("Tracked verification event for certificate ID: {}", certificateId);
            } catch (Exception e) {
                logger.warn("Failed to track verification event: {}", e.getMessage());
            }
        }

        return "verify-certificate";
    }

    /**
     * SHA-256 fingerprint of the signing certificate, as colon-separated uppercase hex.
     * Identifies the certificate without exposing the key.
     */
    static String fingerprintOf(KeyStore keyStore) {
        try {
            Certificate cert = keyStore.getCertificate(KEY_ALIAS);
            if (!(cert instanceof X509Certificate)) {
                return NOT_AVAILABLE;
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            return HexFormat.ofDelimiter(":").withUpperCase().formatHex(digest);
        } catch (Exception e) {
            logger.error("Failed to generate certificate fingerprint: {}", e.getMessage(), e);
            return ERROR;
        }
    }
}
