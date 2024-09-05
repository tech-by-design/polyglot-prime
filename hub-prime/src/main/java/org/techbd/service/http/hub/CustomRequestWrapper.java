package org.techbd.service.http.hub;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CustomRequestWrapper extends HttpServletRequestWrapper {

    private final String requestBody;

    public CustomRequestWrapper(HttpServletRequest request, String requestBody) {
        super(request);
        this.requestBody = requestBody; // Store the custom request body for repeated access
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
                requestBody.getBytes(StandardCharsets.UTF_8));

        return new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }

            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Not implemented as this is not required for simple blocking I/O
            }
        };
    }

    @Override
    public java.io.BufferedReader getReader() throws IOException {
        return new java.io.BufferedReader(new java.io.InputStreamReader(this.getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public int getContentLength() {
        return requestBody.length();
    }

    @Override
    public long getContentLengthLong() {
        return requestBody.length();
    }
}