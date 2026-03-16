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
 * A request wrapper that reads and caches the entire request body on
 * construction, making {@link #getInputStream()} and {@link #getReader()}
 * re-readable any number of times.
 *
 * <p>
 * This is necessary for {@code multipart/related} (MTOM/XOP) requests because
 * Spring cannot bind the raw body to {@code @RequestBody} for that content
 * type, so the {@link jakarta.servlet.ServletInputStream} is consumed by the
 * framework before the controller runs. By wrapping the request in
 * {@link InteractionsFilter} before {@code chain.doFilter()} is called, the
 * original bytes are preserved and available via:
 * <ul>
 *   <li>{@link #getInputStream()} — returns a fresh stream over the cached bytes</li>
 *   <li>{@link #getReader()} — returns a fresh reader over the cached bytes</li>
 *   <li>{@link #getCachedBody()} — direct access to the raw byte array</li>
 * </ul>
 * The cached bytes are also stored as the
 * {@code Constants.RAW_MULTIPART_BODY} request attribute so controllers can
 * access them without touching the stream at all.
 * </p>
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    /**
     * Constructs the wrapper and eagerly reads the entire request body.
     *
     * @param request the original {@link HttpServletRequest}
     * @throws IOException if reading the input stream fails
     */
    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = request.getInputStream().readAllBytes();
    }

    /**
     * Returns a fresh {@link ServletInputStream} backed by the cached body bytes.
     * Can be called multiple times safely.
     */
    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {

            @Override
            public int read() {
                return byteStream.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return byteStream.read(b, off, len);
            }

            @Override
            public boolean isFinished() {
                return byteStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("setReadListener is not supported");
            }
        };
    }

    /**
     * Returns a fresh {@link BufferedReader} backed by the cached body bytes.
     * Can be called multiple times safely.
     */
    @Override
    public BufferedReader getReader() {
        return new BufferedReader(
                new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Returns the raw cached body bytes directly.
     * Prefer this over re-reading the stream when possible.
     *
     * @return the cached request body as a byte array (never {@code null})
     */
    public byte[] getCachedBody() {
        return cachedBody;
    }
}