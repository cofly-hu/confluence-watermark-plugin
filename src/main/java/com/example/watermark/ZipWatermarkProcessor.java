package com.example.watermark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipWatermarkProcessor {

    private static final Logger log = LoggerFactory.getLogger(ZipWatermarkProcessor.class);
    private static final long MAX_ENTRY_SIZE = 100 * 1024 * 1024;
    private static final int MAX_ENTRIES = 10000;

    public static byte[] addWatermark(byte[] zipBytes) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));

            ZipEntry entry;
            int entryCount = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entryCount > MAX_ENTRIES) {
                    log.warn("ZIP has too many entries ({}), aborting watermark", entryCount);
                    zis.close();
                    zos.close();
                    return zipBytes;
                }

                String name = entry.getName();
                byte[] entryBytes = readAll(zis, name);

                if (entryBytes == null) {
                    zis.close();
                    zos.close();
                    return zipBytes;
                }

                if (isPdfFile(name)) {
                    entryBytes = PdfWatermarkProcessor.addWatermark(entryBytes);
                }

                ZipEntry newEntry = new ZipEntry(name);
                if (entry.getComment() != null) {
                    newEntry.setComment(entry.getComment());
                }
                newEntry.setTime(entry.getTime());
                zos.putNextEntry(newEntry);
                zos.write(entryBytes);
                zos.closeEntry();
                zis.closeEntry();
            }

            zis.close();
            zos.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to add watermark to ZIP", e);
            return zipBytes;
        }
    }

    private static byte[] readAll(InputStream is, String entryName) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int bytesRead;
        long totalBytes = 0;
        while ((bytesRead = is.read(data)) != -1) {
            totalBytes += bytesRead;
            if (totalBytes > MAX_ENTRY_SIZE) {
                log.warn("ZIP entry '{}' too large ({} bytes), aborting", entryName, totalBytes);
                return null;
            }
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private static boolean isPdfFile(String name) {
        return name != null && name.toLowerCase().endsWith(".pdf");
    }
}
