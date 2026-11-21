package com.podcastai.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.podcastai.config.AppProperties;
import com.podcastai.web.ApiException;

/**
 * Valida un PDF y saca su texto página a página con Apache PDFBox.
 */
@Component
public class PdfTextExtractor {

    private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private final AppProperties props;

    public PdfTextExtractor(AppProperties props) {
        this.props = props;
    }

    public ExtractedPdf extract(byte[] bytes, String fileName) {
        if (!hasPdfHeader(bytes)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El archivo no es un PDF válido.");
        }
        List<PageText> pages = readPages(bytes);
        boolean allEmpty = pages.stream().allMatch(PageText::isEmpty);
        if (allEmpty) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El PDF no tiene texto seleccionable (parece escaneado). Pronto podremos leerlos.");
        }
        int totalChars = pages.stream().mapToInt(p -> p.text().length()).sum();
        if (totalChars > props.limits().maxSourceChars()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El PDF tiene demasiado texto para un solo podcast. Prueba con un capítulo.");
        }
        return new ExtractedPdf(sha256(bytes), titleFrom(fileName), pages);
    }

    private List<PageText> readPages(byte[] bytes) {
        List<PageText> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            int count = doc.getNumberOfPages();
            if (count > props.limits().maxPdfPages()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "El PDF tiene " + count + " páginas; el máximo es " + props.limits().maxPdfPages() + ".");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int i = 1; i <= count; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pages.add(new PageText(i, normalize(stripper.getText(doc))));
            }
        } catch (InvalidPasswordException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "El PDF está protegido con contraseña.");
        } catch (IOException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "No se ha podido leer el PDF.");
        }
        return pages;
    }

    static boolean hasPdfHeader(byte[] bytes) {
        boolean ok = bytes != null && bytes.length >= PDF_MAGIC.length;
        for (int i = 0; ok && i < PDF_MAGIC.length; i++) {
            ok = bytes[i] == PDF_MAGIC[i];
        }
        return ok;
    }

    static String normalize(String raw) {
        String text = raw.replace(' ', ' ')
                .replaceAll("-\\n(\\p{Ll})", "$1")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n");
        return text.strip();
    }

    static String titleFrom(String fileName) {
        String base = fileName == null || fileName.isBlank() ? "Documento" : fileName;
        String noExt = base.replaceAll("(?i)\\.pdf$", "").replaceAll("[_-]+", " ").strip();
        String title = noExt.isEmpty() ? "Documento" : noExt;
        return title.length() > 80 ? title.substring(0, 80) : title;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
