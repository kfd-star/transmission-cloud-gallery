package com.kfd.cloudgallery.config;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 包装请求，使 InputStream 可以重复读取
 *
 */
@Slf4j
public class RequestWrapper extends HttpServletRequestWrapper {

    private final byte[] bodyBytes;

    public RequestWrapper(HttpServletRequest request) {
        super(request);
        // 读取原始字节，保留UTF-8编码
        byte[] bytes;
        try (InputStream inputStream = request.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            bytes = baos.toByteArray();
        } catch (IOException e) {
            log.error("读取请求体失败", e);
            bytes = new byte[0];
        }
        bodyBytes = bytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bodyBytes);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(this.getInputStream(), StandardCharsets.UTF_8));
    }

    public String getBody() {
        return new String(bodyBytes, StandardCharsets.UTF_8);
    }

}