package com.kousen.cert.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kousen.cert.analytics.service.AnalyticsService;
import com.kousen.cert.analytics.service.CertificateMetadataService;
import com.kousen.cert.model.CertificateRequest;
import com.kousen.cert.analytics.model.CertificateMetadata;
import com.kousen.cert.service.CertificateStorageService;
import com.kousen.cert.service.PdfService;
import com.kousen.cert.service.PdfSigner;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CertificateController.class)
@org.springframework.test.context.ActiveProfiles("test")
class CertificateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PdfService pdfService;

    @MockitoBean
    private PdfSigner pdfSigner;

    @MockitoBean
    private CertificateStorageService storageService;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private CertificateMetadataService metadataService;

    @TempDir
    Path tempDir;

    private static final CertificateRequest VALID_REQUEST = new CertificateRequest(
            "Adm. Grace Hopper", "Making Java Groovy", Optional.of("grace@example.com"));

    @Test
    void shouldCreateCertificateAndReturnPdf() throws Exception {
        // Given
        Path unsigned = blankPdf("unsigned.pdf");
        Path signed = blankPdf("signed.pdf");
        Path stored = tempDir.resolve("stored.pdf");
        when(pdfService.createPdf(any())).thenReturn(unsigned);
        when(pdfSigner.sign(unsigned)).thenReturn(signed);
        when(storageService.storeCertificate(signed, VALID_REQUEST)).thenReturn(stored);

        // When/Then
        mockMvc.perform(post("/api/certificates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(VALID_REQUEST)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"certificate.pdf\""))
                .andExpect(header().string("X-Certificate-Id", matchesPattern("[0-9a-f-]{36}")));

        verify(analyticsService).trackCertificateGenerated(anyString(), eq("Adm. Grace Hopper"),
                eq("grace@example.com"), eq("Making Java Groovy"), anyLong(), any(HttpServletRequest.class));
        verify(metadataService).saveCertificateMetadata(anyString(), eq(stored));
        verify(analyticsService, never()).trackCertificateError(anyString(), any());
        assertThat(unsigned).as("temporary unsigned PDF is cleaned up").doesNotExist();
    }

    @Test
    void shouldPassNullEmailToAnalyticsWhenOmitted() throws Exception {
        Path unsigned = blankPdf("unsigned.pdf");
        when(pdfService.createPdf(any())).thenReturn(unsigned);
        when(pdfSigner.sign(any(Path.class))).thenReturn(blankPdf("signed.pdf"));

        mockMvc.perform(post("/api/certificates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purchaserName\": \"Ada\", \"bookTitle\": \"Kotlin Cookbook\"}"))
                .andExpect(status().isOk());

        verify(analyticsService).trackCertificateGenerated(anyString(), eq("Ada"), isNull(),
                eq("Kotlin Cookbook"), anyLong(), any());
    }

    @Test
    void shouldTrackErrorAndPropagateWhenPdfGenerationFails() throws Exception {
        when(pdfService.createPdf(any())).thenThrow(new IllegalStateException("font subsystem exploded"));

        assertThatThrownBy(() -> mockMvc.perform(post("/api/certificates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(VALID_REQUEST))))
                .hasMessageContaining("font subsystem exploded");

        verify(analyticsService).trackCertificateError(eq("font subsystem exploded"), any());
        verify(analyticsService, never()).trackCertificateGenerated(any(), any(), any(), any(), anyLong(), any());
        verifyNoInteractions(pdfSigner, storageService, metadataService);
    }

    @Test
    void shouldCleanUpUnsignedPdfAndTrackErrorWhenStorageFails() throws Exception {
        Path unsigned = blankPdf("unsigned.pdf");
        when(pdfService.createPdf(any())).thenReturn(unsigned);
        when(pdfSigner.sign(unsigned)).thenReturn(blankPdf("signed.pdf"));
        when(storageService.storeCertificate(any(), any())).thenThrow(new IOException("disk full"));

        assertThatThrownBy(() -> mockMvc.perform(post("/api/certificates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(VALID_REQUEST))))
                .hasMessageContaining("disk full");

        assertThat(unsigned).as("temporary unsigned PDF is cleaned up even on failure").doesNotExist();
        verify(analyticsService).trackCertificateError(eq("disk full"), any());
        verifyNoInteractions(metadataService);
    }

    @Test
    void shouldReturn404ForMissingStoredCertificate() throws Exception {
        when(storageService.getCertificate("nope.pdf")).thenThrow(new IOException("Certificate not found: nope.pdf"));

        mockMvc.perform(get("/api/certificates/stored/nope.pdf"))
                .andExpect(status().isNotFound());

        verify(analyticsService, never()).trackCertificateDownloaded(any(), any());
    }

    @Test
    void shouldTrackDownloadWhenStoredCertificateHasMetadata() throws Exception {
        Path pdf = blankPdf("ada_kotlin_cookbook_20250101000000.pdf");
        String filename = pdf.getFileName().toString();
        CertificateMetadata metadata = mock(CertificateMetadata.class);
        when(metadata.getCertificateId()).thenReturn("cert-42");
        when(storageService.getCertificate(filename)).thenReturn(pdf);
        when(metadataService.getCertificateMetadataByFilename(filename)).thenReturn(metadata);

        mockMvc.perform(get("/api/certificates/stored/" + filename))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"" + filename + "\""));

        verify(analyticsService).trackCertificateDownloaded(eq("cert-42"), any());
    }

    @Test
    void shouldReturn500WhenListingStoredCertificatesFails() throws Exception {
        when(storageService.listAllCertificates()).thenThrow(new IOException("permission denied"));

        mockMvc.perform(get("/api/certificates/stored"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldIncludeSizeAndTimestampForExistingStoredFiles() throws Exception {
        Path pdf = blankPdf("real.pdf");
        when(storageService.listAllCertificates()).thenReturn(List.of(pdf));
        when(storageService.getStoragePath()).thenReturn(tempDir);

        mockMvc.perform(get("/api/certificates/stored"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.certificates[0].filename").value("real.pdf"))
                .andExpect(jsonPath("$.certificates[0].size").value((int) Files.size(pdf)))
                .andExpect(jsonPath("$.certificates[0].lastModified").exists());
    }

    @Test
    void shouldDescribeSignature() throws Exception {
        mockMvc.perform(get("/api/certificates/signature-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatureAlgorithm").value("SHA512withRSA"))
                .andExpect(jsonPath("$.keySize").value("4096 bits"));
    }

    private Path blankPdf(String name) throws IOException {
        Path path = tempDir.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(path.toFile());
        }
        return path;
    }
    
    @Test
    void shouldRejectInvalidBookTitle() throws Exception {
        // Given
        String requestJson = """
                {
                    "purchaserName": "Test User",
                    "bookTitle": "Invalid Book Title",
                    "purchaserEmail": "test@example.com"
                }
                """;

        // When/Then
        mockMvc.perform(post("/api/certificates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors.bookTitle").exists());
    }
    
    @Test
    void shouldRejectBlankPurchaserName() throws Exception {
        mockMvc.perform(post("/api/certificates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"purchaserName": "  ", "bookTitle": "Kotlin Cookbook"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.errors.purchaserName").value("Purchaser name is required"));

        verify(pdfService, never()).createPdf(any());
    }

    @Test
    void shouldRejectMalformedEmail() throws Exception {
        mockMvc.perform(post("/api/certificates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"purchaserName": "Ada", "bookTitle": "Kotlin Cookbook", "purchaserEmail": "not-an-email"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.*").value(org.hamcrest.Matchers.hasItem("Invalid email format")));

        verify(pdfService, never()).createPdf(any());
    }

    @Test
    void shouldReturnAvailableBooks() throws Exception {
        mockMvc.perform(get("/api/certificates/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBooks").isArray())
                .andExpect(jsonPath("$.availableBooks.length()").value(6));
    }
    
    @Test
    void shouldListStoredCertificates() throws Exception {
        // Given
        List<Path> mockCertificates = List.of(
            Paths.get("/test/cert1.pdf"),
            Paths.get("/test/cert2.pdf")
        );
        when(storageService.listAllCertificates()).thenReturn(mockCertificates);
        when(storageService.getStoragePath()).thenReturn(Paths.get("/test"));
        
        // When/Then
        mockMvc.perform(get("/api/certificates/stored"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificates").isArray())
                .andExpect(jsonPath("$.count").value(2));
    }
    
    @Test
    void shouldGetStoredCertificate() throws Exception {
        // Given - create a temporary real file for testing
        Path tempPdf = Files.createTempFile("test-cert-", ".pdf");
        try {
            // Create a simple PDF file
            Files.writeString(tempPdf, "Test PDF content");
            
            // Set up the mock to return the real file
            String filename = tempPdf.getFileName().toString();
            when(storageService.getCertificate(filename)).thenReturn(tempPdf);
            
            // When/Then - should work with a real file
            mockMvc.perform(get("/api/certificates/stored/" + filename))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        } finally {
            // Clean up
            Files.deleteIfExists(tempPdf);
        }
    }
}