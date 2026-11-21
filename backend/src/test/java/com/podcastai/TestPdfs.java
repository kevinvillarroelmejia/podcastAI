package com.podcastai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public final class TestPdfs {

    private TestPdfs() {
    }

    public static byte[] withPages(String... pageTexts) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, 11);
                    cs.newLineAtOffset(50, 700);
                    for (String line : text.split("\n")) {
                        cs.showText(line);
                        cs.newLineAtOffset(0, -14);
                    }
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
