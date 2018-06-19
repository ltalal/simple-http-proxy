package simple.proxy;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import one.util.streamex.EntryStream;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;

@RestController
@EnableConfigurationProperties(SimpleProxyConfiguration.class)
public class SimpleProxy {

    private static final Logger log = LoggerFactory.getLogger(SimpleProxy.class);

    private final URI localUri;
    private final URI remoteUri;
    private final CloseableHttpClient httpClient;
    private final RestTemplate restTemplate;
    private final boolean rewriteHeaders;
    private HttpComponentsClientHttpRequestFactory requestFactory;

    public SimpleProxy(SimpleProxyConfiguration configuration) throws URISyntaxException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        localUri = new URI(configuration.getLocalAddress());
        remoteUri = new URI(configuration.getRemoteAddress());

        HttpClientBuilder builder = HttpClients.custom();
        if (configuration.isTrustAll()) {
            builder.setSSLHostnameVerifier(new NoopHostnameVerifier());
            builder.setSSLSocketFactory(new SSLConnectionSocketFactory(SSLContexts.custom().loadTrustMaterial(null, (chain, authType) -> true).build()));
        }
        httpClient = builder.build();
        requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        restTemplate = new RestTemplate(requestFactory) {
            @Override
            protected void handleResponse(URI url, HttpMethod method, ClientHttpResponse response) {
            }
        };
        rewriteHeaders = configuration.isRewriteHeaders();
    }

    @RequestMapping("**")
    public ResponseEntity<byte[]> proxy(@RequestBody(required = false) byte[] body, HttpMethod method, @RequestHeader HttpHeaders headers, HttpServletRequest request)
        throws Exception {

        URI uri = new URI(remoteUri.getScheme(), remoteUri.getUserInfo(), remoteUri.getHost(), remoteUri.getPort(), request.getRequestURI(), request.getQueryString(), null);

        if (rewriteHeaders)
            headers = replaceHeaders(headers, localUri, remoteUri);

        if (log.isInfoEnabled()) {
            log.info("Redirecting {} {}:\n{}\n\n{}", method, uri, headersToString(headers), bodyToString(body));
        }

        ResponseEntity<byte[]> response = restTemplate.exchange(new RequestEntity<>(body, headers, method, uri), byte[].class);
        
        HttpHeaders responseHeaders = response.getHeaders();
        if (rewriteHeaders)
            responseHeaders = replaceHeaders(response.getHeaders(), remoteUri, localUri);

        if (log.isInfoEnabled()) {
            log.info("Got response {} {}:\n{} {}\n{}\n\n{}", method, uri, response.getStatusCode(), response.getStatusCode().getReasonPhrase(), headersToString(responseHeaders), bodyToString(response.getBody()));
        }

        return new ResponseEntity<>(response.getBody(), responseHeaders, response.getStatusCode());
    }

    private String headersToString(@RequestHeader HttpHeaders headers) {
        return EntryStream.of(headers).flatMapValues(Collection::stream).join(": ").joining("\n");
    }

    private String bodyToString(byte[] body) {
        if (body == null) return "";
        if (body.length < 65536) {
            try {
                String text = new String(body, StandardCharsets.UTF_8);
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                if (Arrays.equals(body, bytes)) {
                    return text;
                }
            } catch (Exception ignored) {
            }
        }
        return "Body " + body.length + " bytes";
    }

    private HttpHeaders replaceHeaders(HttpHeaders headers, URI from, URI to) {
        HttpHeaders result = new HttpHeaders();
        headers.forEach((k,v) -> result.put(k, replaceHeader(v, from, to)));
        return result;
    }

    private List<String> replaceHeader(List<String> v, URI from, URI to) {
        return v.stream().map(h->replaceHeader(h, from, to)).collect(Collectors.toList());
    }

    private String replaceHeader(String header, URI from, URI to) {
        header = StringUtils.replace(header, from.toString(), to.toString());
        return StringUtils.replace(header, from.getHost(), to.getHost());
    }
}
