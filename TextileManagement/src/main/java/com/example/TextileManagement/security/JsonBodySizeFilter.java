package com.example.TextileManagement.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JsonBodySizeFilter extends OncePerRequestFilter {
    private final int maxBytes;

    public JsonBodySizeFilter(@Value("${app.request.max-json-bytes:262144}") int maxBytes) {
        this.maxBytes = Math.max(1024, maxBytes);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isJson(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > maxBytes) {
            reject(response);
            return;
        }
        try {
            filterChain.doFilter(new LimitedBodyRequest(request, maxBytes), response);
        } catch (BodyTooLargeException exception) {
            reject(response);
        }
    }

    private boolean isJson(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null
                && (contentType.toLowerCase(java.util.Locale.ROOT).startsWith("application/json")
                        || contentType.toLowerCase(java.util.Locale.ROOT).contains("+json"));
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Request body is too large\"}");
    }

    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {
        private final int maxBytes;
        private ServletInputStream inputStream;

        private LimitedBodyRequest(HttpServletRequest request, int maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new LimitedInputStream(super.getInputStream(), maxBytes);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final int maxBytes;
        private int bytesRead;

        private LimitedInputStream(ServletInputStream delegate, int maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            if (bytesRead >= maxBytes) {
                if (delegate.read() != -1) {
                    throw new BodyTooLargeException();
                }
                return -1;
            }
            int value = delegate.read();
            if (value != -1) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (bytesRead >= maxBytes) {
                return read() == -1 ? -1 : 0;
            }
            int read = delegate.read(bytes, offset, Math.min(length, maxBytes - bytesRead));
            if (read > 0) {
                bytesRead += read;
            }
            return read;
        }

        @Override
        public boolean isFinished() {
            return bytesRead >= maxBytes;
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }

    private static final class BodyTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
