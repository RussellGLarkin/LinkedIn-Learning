package com.kousen.cert.controller;

import com.kousen.cert.analytics.service.AnalyticsService;
import com.kousen.cert.service.QrCodeGenerator;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Exercises VerificationController exactly as Spring wires it (no hand-built bean),
 * using the real test keystore so the fingerprint logic actually runs.
 */
@WebMvcTest(VerificationController.class)
// KeyStoreProvider creates this keystore if it is missing (*.p12 files are gitignored)
@TestPropertySource(properties = "certificate.keystore=${java.io.tmpdir}/verification-test-keystore.p12")
class VerificationControllerTest {

    private static final String SHA256_FINGERPRINT = "^([0-9A-F]{2}:){31}[0-9A-F]{2}$";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean  // required by CertificateConfig, which the MVC slice loads as a WebMvcConfigurer
    private QrCodeGenerator qrCodeGenerator;

    @Test
    void shouldShowRealKeystoreFingerprint() throws Exception {
        mockMvc.perform(get("/verify-certificate"))
                .andExpect(status().isOk())
                .andExpect(view().name("verify-certificate"))
                .andExpect(model().attribute("certificateFingerprint", matchesPattern(SHA256_FINGERPRINT)));
    }

    @Test
    void shouldTrackVerificationWhenIdProvided() throws Exception {
        mockMvc.perform(get("/verify-certificate").param("id", "cert-123"))
                .andExpect(status().isOk());

        verify(analyticsService).trackCertificateVerified(eq("cert-123"), any(HttpServletRequest.class));
    }

    @Test
    void shouldNotTrackVerificationWhenIdMissing() throws Exception {
        mockMvc.perform(get("/verify-certificate")).andExpect(status().isOk());

        verify(analyticsService, never()).trackCertificateVerified(anyString(), any());
    }

    @Test
    void shouldNotTrackVerificationWhenIdBlank() throws Exception {
        mockMvc.perform(get("/verify-certificate").param("id", "")).andExpect(status().isOk());

        verify(analyticsService, never()).trackCertificateVerified(anyString(), any());
    }

    @Test
    void shouldStillRenderPageWhenAnalyticsFails() throws Exception {
        doThrow(new RuntimeException("analytics down"))
                .when(analyticsService).trackCertificateVerified(anyString(), any());

        mockMvc.perform(get("/verify-certificate").param("id", "cert-123").param("name", "Ada"))
                .andExpect(status().isOk())
                .andExpect(view().name("verify-certificate"))
                .andExpect(model().attribute("name", "Ada"));
    }

    @Test
    void shouldPassProvidedParametersToModel() throws Exception {
        mockMvc.perform(get("/verify-certificate")
                        .param("name", "John Doe")
                        .param("book", "Modern Java Recipes")
                        .param("date", "2025-04-18"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("name", "John Doe"))
                .andExpect(model().attribute("bookTitle", "Modern Java Recipes"))
                .andExpect(model().attribute("issueDate", "2025-04-18"));
    }

    @Test
    void shouldDefaultMissingParametersToNotSpecified() throws Exception {
        mockMvc.perform(get("/verify-certificate"))
                .andExpect(model().attribute("name", "Not specified"))
                .andExpect(model().attribute("bookTitle", "Not specified"))
                .andExpect(model().attribute("issueDate", "Not specified"));
    }
}
