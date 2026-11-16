package com.kousen.cert.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the production path ({@code createCertificatePdfWithQrData}) and the
 * degraded paths: missing resources, unreadable QR images, and failed saves.
 */
class PdfBoxGeneratorFallbackTest {

    private static final String TITLE = "Certificate of Ownership";
    private static final String NAME = "Ada Lovelace";
    private static final String BOOK = "Modern Java Recipes";
    private static final String FULL_QR_CAPTION = "Scan to verify certificate authenticity";

    private static final PdfBoxGenerator.ResourceOpener CLASSPATH = path -> {
        var resource = new ClassPathResource(path);
        return resource.exists() ? resource.getInputStream() : null;
    };
    private static final PdfBoxGenerator.DocumentWriter SAVE = (doc, path) -> doc.save(path.toFile());

    @TempDir
    Path tempDir;

    private final List<Path> generated = new ArrayList<>();

    @AfterEach
    void cleanUp() throws IOException {
        for (Path p : generated) {
            Files.deleteIfExists(p);
        }
    }

    // --- createCertificatePdfWithQrData (the method PdfService uses in production) ---

    @Test
    void shouldRenderQrCodeAndCaptionFromInMemoryData() throws Exception {
        Path pdf = track(new PdfBoxGenerator().createCertificatePdfWithQrData(TITLE, NAME, BOOK, qrBytes()));

        Rendered r = inspect(pdf);
        assertThat(r.text).contains(TITLE, NAME, BOOK, "This certifies that", FULL_QR_CAPTION);
        assertThat(r.imageCount).as("background + QR code").isEqualTo(2);
        assertThat(r.fontNames).anyMatch(f -> f.contains("CinzelDecorative"))
                .anyMatch(f -> f.contains("GreatVibes"));
    }

    @Test
    void shouldOmitQrCodeWhenDataIsNull() throws Exception {
        Path pdf = track(new PdfBoxGenerator().createCertificatePdfWithQrData(TITLE, NAME, BOOK, null));

        Rendered r = inspect(pdf);
        assertThat(r.text).contains(NAME).doesNotContain("Scan to verify");
        assertThat(r.imageCount).as("background only").isEqualTo(1);
    }

    @Test
    void shouldOmitQrCodeWhenDataIsEmpty() throws Exception {
        Path pdf = track(new PdfBoxGenerator().createCertificatePdfWithQrData(TITLE, NAME, BOOK, new byte[0]));

        Rendered r = inspect(pdf);
        assertThat(r.text).doesNotContain("Scan to verify");
        assertThat(r.imageCount).isEqualTo(1);
    }

    @Test
    void shouldPropagateErrorForCorruptQrData() {
        byte[] notAnImage = "definitely not a PNG".getBytes();

        assertThatThrownBy(() -> new PdfBoxGenerator().createCertificatePdfWithQrData(TITLE, NAME, BOOK, notAnImage))
                .isInstanceOf(Exception.class);
    }

    // --- createCertificatePdf (file-based QR) ---

    @Test
    void shouldDrawPlaceholderWhenQrFileIsNotAnImage() throws Exception {
        Path bogusQr = Files.writeString(tempDir.resolve("qr.png"), "not an image");

        Path pdf = track(new PdfBoxGenerator().createCertificatePdf(TITLE, NAME, BOOK, bogusQr));

        Rendered r = inspect(pdf);
        assertThat(r.text).contains(FULL_QR_CAPTION);
        assertThat(r.imageCount).as("background only; QR replaced by a rectangle").isEqualTo(1);
    }

    @Test
    void shouldOmitQrCodeWhenFileDoesNotExist() throws Exception {
        Path pdf = track(new PdfBoxGenerator().createCertificatePdf(TITLE, NAME, BOOK, tempDir.resolve("missing.png")));

        assertThat(inspect(pdf).text).contains(NAME).doesNotContain("Scan to verify");
    }

    // --- missing or unreadable resources ---

    @Test
    void shouldFallBackToStandardFontsWhenFontFilesAreMissing() throws Exception {
        PdfBoxGenerator.ResourceOpener noFonts = path -> path.startsWith("fonts/") ? null : CLASSPATH.open(path);

        Path pdf = track(new PdfBoxGenerator(noFonts, SAVE).createCertificatePdfWithQrData(TITLE, NAME, BOOK, qrBytes()));

        Rendered r = inspect(pdf);
        assertThat(r.text).contains(TITLE, NAME, BOOK);
        assertThat(r.fontNames).noneMatch(f -> f.contains("Cinzel") || f.contains("GreatVibes"))
                .contains("Helvetica-Bold", "Helvetica-Oblique");
    }

    @Test
    void shouldFallBackToStandardFontsWhenFontLoadingFails() throws Exception {
        PdfBoxGenerator.ResourceOpener brokenFonts = path -> {
            if (path.startsWith("fonts/")) {
                throw new IOException("disk error");
            }
            return CLASSPATH.open(path);
        };

        Path pdf = track(new PdfBoxGenerator(brokenFonts, SAVE).createCertificatePdf(TITLE, NAME, BOOK, null));

        assertThat(inspect(pdf).fontNames).contains("Helvetica-Bold", "Helvetica-Oblique");
    }

    @Test
    void shouldRenderWithoutBackgroundWhenImageIsMissing() throws Exception {
        PdfBoxGenerator.ResourceOpener noImages = path -> path.startsWith("images/") ? null : CLASSPATH.open(path);

        Path pdf = track(new PdfBoxGenerator(noImages, SAVE).createCertificatePdfWithQrData(TITLE, NAME, BOOK, null));

        Rendered r = inspect(pdf);
        assertThat(r.text).contains(NAME);
        assertThat(r.imageCount).isZero();
    }

    @Test
    void shouldRenderWithoutBackgroundWhenImageCannotBeRead() throws Exception {
        PdfBoxGenerator.ResourceOpener brokenImage = path -> path.startsWith("images/")
                ? new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("read failure");
                    }
                }
                : CLASSPATH.open(path);

        Path pdf = track(new PdfBoxGenerator(brokenImage, SAVE).createCertificatePdf(TITLE, NAME, BOOK, null));

        assertThat(inspect(pdf).imageCount).isZero();
    }

    // --- save failures ---

    @Test
    void shouldRetryWithStandardFontsWhenFirstSaveFails() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        PdfBoxGenerator.DocumentWriter failOnce = (doc, path) -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IOException("simulated font-embedding failure");
            }
            SAVE.write(doc, path);
        };

        Path pdf = track(new PdfBoxGenerator(CLASSPATH, failOnce)
                .createCertificatePdfWithQrData(TITLE, NAME, BOOK, qrBytes()));

        Rendered r = inspect(pdf);
        assertThat(attempts).hasValue(2);
        assertThat(r.text).contains(TITLE, NAME, BOOK, "Scan to verify").doesNotContain(FULL_QR_CAPTION);
        assertThat(r.fontNames).containsOnly("Helvetica", "Helvetica-Bold", "Helvetica-Oblique");
        assertThat(r.imageCount).as("QR only; simplified version has no background").isEqualTo(1);
    }

    @Test
    void shouldThrowWhenFallbackSaveAlsoFails() {
        PdfBoxGenerator.DocumentWriter alwaysFail = (doc, path) -> {
            throw new IOException("disk full");
        };

        assertThatThrownBy(() -> new PdfBoxGenerator(CLASSPATH, alwaysFail).createCertificatePdf(TITLE, NAME, BOOK, null))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Failed to save PDF document")
                .hasMessageContaining("disk full");
    }

    // --- helpers ---

    private record Rendered(String text, int imageCount, List<String> fontNames) {}

    private Path track(Path pdf) {
        generated.add(pdf);
        return pdf;
    }

    private static byte[] qrBytes() throws IOException {
        try (InputStream in = new ClassPathResource("images/test-qr.png").getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static Rendered inspect(Path pdf) throws IOException {
        assertThat(pdf).exists().isNotEmptyFile();
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            PDResources res = doc.getPage(0).getResources();

            int images = 0;
            for (COSName xName : res.getXObjectNames()) {
                if (res.isImageXObject(xName)) {
                    images++;
                }
            }
            List<String> fonts = new ArrayList<>();
            for (COSName fName : res.getFontNames()) {
                fonts.add(res.getFont(fName).getName());
            }
            return new Rendered(new PDFTextStripper().getText(doc), images, fonts);
        }
    }
}
