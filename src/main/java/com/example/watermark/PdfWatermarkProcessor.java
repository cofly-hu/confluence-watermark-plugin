package com.example.watermark;

import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PdfWatermarkProcessor {

    private static final Logger log = LoggerFactory.getLogger(PdfWatermarkProcessor.class);
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    public static byte[] addWatermark(byte[] pdfBytes) {
        PDDocument document = null;
        try {
            ConfluenceUser user = AuthenticatedUserThreadLocal.get();
            String username = (user != null) ? user.getName() : "anonymous";
            String timestamp = DATE_FORMAT.get().format(new Date());
            String watermarkText = username + " | " + timestamp;

            document = PDDocument.load(new ByteArrayInputStream(pdfBytes));
            PDType1Font font = PDType1Font.HELVETICA;

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                PDPage page = document.getPage(i);
                PDRectangle pageSize = page.getMediaBox();
                float pageWidth = pageSize.getWidth();
                float pageHeight = pageSize.getHeight();

                PDPageContentStream cs = new PDPageContentStream(document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true);

                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(0.15f);
                gs.setStrokingAlphaConstant(0.15f);

                cs.saveGraphicsState();
                cs.setGraphicsStateParameters(gs);
                cs.setNonStrokingColor(Color.GRAY);
                cs.beginText();
                cs.setFont(font, 14);
                cs.setTextMatrix(
                        (float) Math.cos(Math.toRadians(45)),
                        (float) Math.sin(Math.toRadians(45)),
                        (float) -Math.sin(Math.toRadians(45)),
                        (float) Math.cos(Math.toRadians(45)),
                        pageWidth / 4, pageHeight / 2);
                cs.showText(watermarkText);
                cs.endText();
                cs.restoreGraphicsState();
                cs.close();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (Exception e) {
            log.error("Failed to add watermark to PDF", e);
            return pdfBytes;
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static InputStream addWatermark(InputStream pdfStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int bytesRead;
        while ((bytesRead = pdfStream.read(data)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        byte[] watermarkedBytes = addWatermark(buffer.toByteArray());
        return new ByteArrayInputStream(watermarkedBytes);
    }
}
