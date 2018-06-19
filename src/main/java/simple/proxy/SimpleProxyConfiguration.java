package simple.proxy;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("proxy")
public class SimpleProxyConfiguration {
    private String localAddress;
    private String remoteAddress;
    private boolean trustAll;
    private boolean rewriteHeaders;

    public String getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public boolean isTrustAll() {
        return trustAll;
    }

    public void setTrustAll(boolean trustAll) {
        this.trustAll = trustAll;
    }

    public boolean isRewriteHeaders() {
        return rewriteHeaders;
    }

    public void setRewriteHeaders(boolean rewriteHeaders) {
        this.rewriteHeaders = rewriteHeaders;
    }
}
