package com.example.watermark;

import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ImageWatermarkProcessor {

    private static final Logger log = LoggerFactory.getLogger(ImageWatermarkProcessor.class);
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    public static byte[] addWatermark(byte[] imageBytes, String formatName) {
        try {
            ConfluenceUser user = AuthenticatedUserThreadLocal.get();
            String username = (user != null) ? user.getName() : "anonymous";
            String timestamp = DATE_FORMAT.get().format(new Date());
            String watermarkText = username + " | " + timestamp;

            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (originalImage == null) return imageBytes;

            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            boolean isJpg = "jpg".equalsIgnoreCase(formatName) || "jpeg".equalsIgnoreCase(formatName);
            int imageType = isJpg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;

            BufferedImage watermarkedImage = new BufferedImage(width, height, imageType);
            Graphics2D g2d = watermarkedImage.createGraphics();
            g2d.drawImage(originalImage, 0, 0, null);

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Font font = new Font("Arial", Font.PLAIN, Math.max(12, Math.min(width, height) / 20));
            g2d.setFont(font);

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
            g2d.setColor(Color.GRAY);

            AffineTransform originalTransform = g2d.getTransform();
            AffineTransform transform = new AffineTransform();
            transform.setToRotation(Math.toRadians(-45), width / 2.0, height / 2.0);
            g2d.setTransform(transform);

            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(watermarkText);
            int x = (width - textWidth) / 2;
            int y = (height + fm.getHeight()) / 2;
            g2d.drawString(watermarkText, x, y);

            g2d.setTransform(originalTransform);
            g2d.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(watermarkedImage, formatName, outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Failed to add watermark to image", e);
            return imageBytes;
        }
    }

    public static String getFormatFromContentType(String contentType) {
        if (contentType == null) return "png";
        if (contentType.contains("jpeg") || contentType.contains("jpg")) return "jpg";
        if (contentType.contains("gif")) return "gif";
        if (contentType.contains("bmp")) return "bmp";
        return "png";
    }
}
