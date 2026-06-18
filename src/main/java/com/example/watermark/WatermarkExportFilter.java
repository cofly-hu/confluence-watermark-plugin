package com.example.watermark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class WatermarkExportFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(WatermarkExportFilter.class);
    private static final long MAX_WATERMARK_SIZE = 50 * 1024 * 1024;
    private static final int CACHE_MAX_SIZE = 1000;

    private static final byte TYPE_SKIP = 0;
    private static final byte TYPE_PDF = 1;
    private static final byte TYPE_ZIP = 2;

    private static final Map<String, Byte> ROUTE_CACHE = new LinkedHashMap<String, Byte>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Byte> eldest) {
            return size() > CACHE_MAX_SIZE;
        }
    };

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();
        String queryString = httpRequest.getQueryString() != null ? httpRequest.getQueryString() : "";

        Byte cachedType = ROUTE_CACHE.get(uri);
        if (cachedType != null) {
            switch (cachedType) {
                case TYPE_PDF:
                    handlePdfWatermark(httpRequest, httpResponse, chain);
                    return;
                case TYPE_ZIP:
                    handleZipWatermark(httpRequest, httpResponse, chain);
                    return;
                default:
                    chain.doFilter(request, response);
                    return;
            }
        }

        boolean isPdfExport = uri.contains("exportpdf") || uri.contains("exportPageToPdf") ||
                uri.contains("/spaces/flyingpdf/pdfpageexport.action");
        boolean isPdfAttachment = uri.contains("/download/") && uri.endsWith(".pdf");
        boolean isZipDownload = uri.endsWith(".zip") || queryString.contains("zip=true") ||
                uri.contains("downloadAllAttachments") || uri.contains("batchDownloadAttachments");

        if (isPdfExport || isPdfAttachment) {
            ROUTE_CACHE.put(uri, TYPE_PDF);
            handlePdfWatermark(httpRequest, httpResponse, chain);
        } else if (isZipDownload) {
            ROUTE_CACHE.put(uri, TYPE_ZIP);
            handleZipWatermark(httpRequest, httpResponse, chain);
        } else {
            ROUTE_CACHE.put(uri, TYPE_SKIP);
            chain.doFilter(request, response);
        }
    }

    private void handlePdfWatermark(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        WatermarkResponseWrapper wrapper = new WatermarkResponseWrapper(response);
        chain.doFilter(request, wrapper);

        byte[] originalBytes = wrapper.getBytes();
        if (originalBytes != null && originalBytes.length > 0) {
            if (originalBytes.length > MAX_WATERMARK_SIZE) {
                log.warn("PDF too large for watermark ({} bytes), skipping", originalBytes.length);
                writeResponse(response, originalBytes, "application/pdf");
                return;
            }
            byte[] watermarkedBytes = PdfWatermarkProcessor.addWatermark(originalBytes);
            writeResponse(response, watermarkedBytes, "application/pdf");
        }
    }

    private void handleZipWatermark(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        WatermarkResponseWrapper wrapper = new WatermarkResponseWrapper(response);
        chain.doFilter(request, wrapper);

        byte[] originalBytes = wrapper.getBytes();
        if (originalBytes != null && originalBytes.length > 0) {
            if (originalBytes.length > MAX_WATERMARK_SIZE) {
                log.warn("ZIP too large for watermark ({} bytes), skipping", originalBytes.length);
                writeResponse(response, originalBytes, "application/zip");
                return;
            }
            byte[] watermarkedBytes = ZipWatermarkProcessor.addWatermark(originalBytes);
            writeResponse(response, watermarkedBytes, "application/zip");
        }
    }

    private void writeResponse(ServletResponse response, byte[] bytes, String contentType) throws IOException {
        response.setContentType(contentType);
        response.setContentLength(bytes.length);
        OutputStream out = response.getOutputStream();
        out.write(bytes);
        out.flush();
    }

    @Override
    public void destroy() {
        ROUTE_CACHE.clear();
    }
}
