package com.kousen.cert.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF generation utility using Apache PDFBox
 */
@Component
public class PdfBoxGenerator {

    private static final Logger logger = LoggerFactory.getLogger(PdfBoxGenerator.class);

    private static final Color GOLD_COLOR = new Color(255, 214, 92);
    private static final String TITLE_FONT = "CinzelDecorative-Regular.ttf";
    private static final String NAME_FONT = "GreatVibes-Regular.ttf";
    private static final String BACKGROUND_IMAGE = "images/certificate-bg.png";
    private static final float QR_X = 80f;
    private static final float QR_Y = 80f;
    private static final float QR_SIZE = 100f;

    /** Opens a classpath-relative resource; returns {@code null} if it does not exist. */
    @FunctionalInterface
    interface ResourceOpener {
        InputStream open(String path) throws IOException;
    }

    /** Writes a finished document to disk. */
    @FunctionalInterface
    interface DocumentWriter {
        void write(PDDocument document, Path path) throws IOException;
    }

    /** Draws a QR code onto the page at the given position. */
    @FunctionalInterface
    private interface QrDrawer {
        void draw(PDDocument document, PDPageContentStream contentStream, float x, float y) throws IOException;
    }

    private static final ResourceOpener CLASSPATH = path -> {
        var resource = new ClassPathResource(path);
        return resource.exists() ? resource.getInputStream() : null;
    };

    private final ResourceOpener resources;
    private final DocumentWriter writer;

    public PdfBoxGenerator() {
        this(CLASSPATH, (document, path) -> document.save(path.toFile()));
    }

    /** Test seam: lets tests simulate missing resources or a failing first save. */
    PdfBoxGenerator(ResourceOpener resources, DocumentWriter writer) {
        this.resources = resources;
        this.writer = writer;
    }

    /**
     * Creates a certificate PDF for the specified text content
     *
     * @param title The main title text
     * @param name The recipient's name
     * @param subtitle The subtitle or book title
     * @param qrCodePath Path to the QR code image (may be null or missing)
     * @return Path to the generated PDF file
     * @throws IOException If there's an error during PDF creation
     */
    public Path createCertificatePdf(String title, String name, String subtitle, Path qrCodePath) throws IOException {
        QrDrawer qr = (qrCodePath != null && Files.exists(qrCodePath))
                ? (doc, cs, x, y) -> drawQrFromFile(doc, cs, qrCodePath, x, y)
                : null;
        return render(title, name, subtitle, qr);
    }

    /**
     * Creates a certificate PDF using in-memory QR code data (avoids temporary image files).
     *
     * @param title        The main title text
     * @param name         The recipient's name
     * @param subtitle     The subtitle or book title
     * @param qrCodeData   QR code image bytes in PNG format (may be null or empty)
     * @return Path to the generated PDF file
     * @throws IOException If there's an error during PDF creation
     */
    public Path createCertificatePdfWithQrData(String title, String name, String subtitle, byte[] qrCodeData) throws IOException {
        QrDrawer qr = (qrCodeData != null && qrCodeData.length > 0)
                ? (doc, cs, x, y) -> cs.drawImage(
                        PDImageXObject.createFromByteArray(doc, qrCodeData, "qrcode.png"), x, y, QR_SIZE, QR_SIZE)
                : null;
        return render(title, name, subtitle, qr);
    }

    private Path render(String title, String name, String subtitle, QrDrawer qr) throws IOException {
        Path pdfPath = Files.createTempFile("cert-", ".pdf");

        // Landscape A4 dimensions in points (1/72 inch)
        float pageWidth = PDRectangle.A4.getHeight();
        float pageHeight = PDRectangle.A4.getWidth();

        try (var document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
            document.addPage(page);

            // PDType0Font must be loaded per document in PDFBox 3.0.4, so fonts are not cached
            PDFont titleFont = getFont(document, TITLE_FONT, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
            PDFont nameFont = getFont(document, NAME_FONT, new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE));
            PDFont textFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            addBackgroundImage(document, page);

            try (PDPageContentStream contentStream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true)) {
                drawBody(contentStream, pageWidth / 2, pageHeight - 200,
                        titleFont, 48, nameFont, 40, titleFont, 24, textFont, title, name, subtitle);

                if (qr != null) {
                    qr.draw(document, contentStream, QR_X, QR_Y);
                    // Center "Scan to verify certificate authenticity" below the QR code
                    drawCenteredText(contentStream, textFont, 8, "Scan to verify certificate authenticity",
                            QR_X + QR_SIZE / 2f, QR_Y - 20f);
                }
            }

            try {
                // Explicitly disable font subsetting, which caused issues on Heroku
                document.getDocumentCatalog().getAcroForm();  // Initialize AcroForm if present
                document.setAllSecurityToBeRemoved(true);
                System.setProperty("org.apache.pdfbox.font.subset", "false");
                document.getDocumentInformation().setCustomMetadataValue("DisableFontSubsetting", "true");

                writer.write(document, pdfPath);
                logger.debug("PDF created at: {}", pdfPath.toAbsolutePath());
            } catch (Exception e) {
                logger.warn("Error saving PDF ({}); retrying with standard fonts only", e.getMessage());
                renderWithStandardFonts(pdfPath, pageWidth, pageHeight, title, name, subtitle, qr);
            }
        }

        return pdfPath;
    }

    private void renderWithStandardFonts(Path pdfPath, float pageWidth, float pageHeight,
                                         String title, String name, String subtitle, QrDrawer qr) throws IOException {
        try (PDDocument simpleDoc = new PDDocument()) {
            PDPage simplePage = new PDPage(new PDRectangle(pageWidth, pageHeight));
            simpleDoc.addPage(simplePage);

            try (PDPageContentStream contentStream = new PDPageContentStream(
                    simpleDoc, simplePage, PDPageContentStream.AppendMode.APPEND, true)) {
                PDFont stdFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                PDFont stdBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDFont stdItalic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

                drawBody(contentStream, pageWidth / 2, pageHeight - 200,
                        stdBold, 36, stdItalic, 32, stdBold, 22, stdFont, title, name, subtitle);

                if (qr != null) {
                    qr.draw(simpleDoc, contentStream, QR_X, QR_Y);
                    drawCenteredText(contentStream, stdFont, 8, "Scan to verify", QR_X, 60);
                }
            }

            writer.write(simpleDoc, pdfPath);
            logger.info("Created simplified PDF with standard fonts only");
        } catch (Exception e) {
            logger.error("Error saving even with simplified approach: {}", e.getMessage());
            throw new IOException("Failed to save PDF document: " + e.getMessage(), e);
        }
    }

    private void drawBody(PDPageContentStream contentStream, float centerX, float y,
                          PDFont titleFont, float titleSize, PDFont nameFont, float nameSize,
                          PDFont subtitleFont, float subtitleSize, PDFont textFont,
                          String title, String name, String subtitle) throws IOException {
        contentStream.setNonStrokingColor(GOLD_COLOR);

        drawCenteredText(contentStream, titleFont, titleSize, title, centerX, y);
        y -= 50;
        drawCenteredText(contentStream, textFont, 14, "This certifies that", centerX, y);
        y -= 60;
        drawCenteredText(contentStream, nameFont, nameSize, name, centerX, y);
        y -= 50;
        drawCenteredText(contentStream, textFont, 14, "is the proud owner of", centerX, y);
        y -= 40;
        drawCenteredText(contentStream, subtitleFont, subtitleSize, subtitle, centerX, y);
        y -= 50;
        drawCenteredText(contentStream, textFont, 14, "and has earned the author's eternal gratitude.", centerX, y);
    }

    /**
     * Loads a TrueType font from the fonts/ resource folder, falling back to a standard font.
     */
    private PDFont getFont(PDDocument document, String fontFileName, PDFont fallbackFont) {
        try (InputStream fontStream = resources.open("fonts/" + fontFileName)) {
            if (fontStream == null) {
                logger.warn("Font file not found: {}; using {}", fontFileName, fallbackFont.getName());
                return fallbackFont;
            }
            // embedSubset=false: subsetting caused issues on Heroku
            return PDType0Font.load(document, fontStream, false);
        } catch (IOException e) {
            logger.warn("Error loading font {}: {}; using {}", fontFileName, e.getMessage(), fallbackFont.getName());
            return fallbackFont;
        }
    }

    /**
     * Draws the background image at full page size; continues without one if it can't be loaded.
     */
    private void addBackgroundImage(PDDocument document, PDPage page) {
        try (InputStream imageStream = resources.open(BACKGROUND_IMAGE)) {
            if (imageStream == null) {
                logger.warn("Background image not found: {}", BACKGROUND_IMAGE);
                return;
            }
            PDImageXObject backgroundImage = PDImageXObject.createFromByteArray(document,
                    imageStream.readAllBytes(), "background");
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.PREPEND, true)) {
                contentStream.drawImage(backgroundImage, 0, 0,
                        page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
            }
        } catch (IOException e) {
            logger.warn("Error adding background image: {}", e.getMessage());
        }
    }

    /**
     * Draws a QR code image file; draws a placeholder rectangle if the file can't be read as an image.
     */
    private void drawQrFromFile(PDDocument document, PDPageContentStream contentStream,
                                Path qrCodePath, float x, float y) throws IOException {
        try {
            PDImageXObject qrCode = PDImageXObject.createFromFile(qrCodePath.toString(), document);
            contentStream.drawImage(qrCode, x, y, QR_SIZE, QR_SIZE);
        } catch (IOException | RuntimeException e) {
            // PDFBox throws NullPointerException (not IOException) for a .png that isn't a valid image
            logger.warn("Error adding QR code: {}", e.toString());
            contentStream.setStrokingColor(Color.BLACK);
            contentStream.addRect(x, y, QR_SIZE, QR_SIZE);
            contentStream.stroke();
        }
    }

    private void drawCenteredText(PDPageContentStream contentStream, PDFont font,
                                  float fontSize, String text, float centerX, float y) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000 * fontSize;
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(centerX - textWidth / 2, y);
        contentStream.showText(text);
        contentStream.endText();
    }
}
