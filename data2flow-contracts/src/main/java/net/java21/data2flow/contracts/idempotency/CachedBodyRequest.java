package net.java21.data2flow.contracts.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 본문을 미리 다 읽어 두는 요청. 멱등 키 해시를 컨트롤러보다 먼저 계산하고, 컨트롤러는 같은 본문을 다시 읽는다.
 *
 * <p>multipart·form 본문은 미리 읽지 않는다({@link #deferred}). 서블릿 컨테이너가 원문 스트림에서 파트·파라미터를 직접 읽어야 하므로,
 * 먼저 읽어 버리면 파일 업로드가 500(Stream closed)이 된다. 이때 해시는 {@link IdempotencyInterceptor}가 파트·파라미터로 계산한다.
 */
class CachedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    private CachedBodyRequest(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body;
    }

    /** 본문을 읽지 않고 표시만 한다(multipart·form). 컨테이너가 파트·파라미터를 그대로 읽는다 */
    static CachedBodyRequest deferred(HttpServletRequest request) {
        return new CachedBodyRequest(request, null);
    }

    /** 본문을 미리 읽지 않은 요청이면 true */
    boolean deferred() {
        return body == null;
    }

    /** 본문이 {@code maxBytes}를 넘으면 {@link PayloadTooLargeException} */
    static CachedBodyRequest read(HttpServletRequest request, int maxBytes) throws IOException {
        try (InputStream in = request.getInputStream()) {
            byte[] bytes = in.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw new PayloadTooLargeException();
            }
            return new CachedBodyRequest(request, bytes);
        }
    }

    byte[] body() {
        return body == null ? new byte[0] : body.clone();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (body == null) {
            return super.getInputStream();
        }
        ByteArrayInputStream in = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return in.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("비동기 읽기는 지원하지 않습니다");
            }

            @Override
            public int read() {
                return in.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (body == null) {
            return super.getReader();
        }
        String encoding = getCharacterEncoding();
        Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    static class PayloadTooLargeException extends IOException {
    }
}
