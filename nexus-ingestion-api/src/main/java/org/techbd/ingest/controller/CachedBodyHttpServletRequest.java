package org.techbd.ingest.controller;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Wraps an HttpServletRequest and caches the body bytes so that
 * getInputStream() can be called multiple times.
 *
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    /**
     * Reads and caches the entire request body from the underlying stream.
     * Must be called BEFORE any Spring component has a chance to call
     * getParameter() or getInputStream().
     */
    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        // Read raw bytes directly from the stream before anything else can touch it.
        // If Spring already consumed it, this will return 0 bytes and the body will be lost. 
        // That's why this wrapper must be applied early in the filter chain 
        this.cachedBody = request.getInputStream().readAllBytes();
    }

    /**
     * Returns the raw cached body bytes. Safe to call multiple times.
     */
    public byte[] getCachedBody() {
        return cachedBody;
    }

    /**
     * Returns a fresh InputStream over the cached bytes each time it is called.
     * This makes the body re-readable by any downstream filter or controller.
     */
    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override public int read()                              { return bais.read(); }
            @Override public int read(byte[] b, int off, int len)   { return bais.read(b, off, len); }
            @Override public boolean isFinished()                    { return bais.available() == 0; }
            @Override public boolean isReady()                       { return true; }
            @Override public void setReadListener(ReadListener rl)   { /* no-op */ }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(
                new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

}