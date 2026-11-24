package com.flowpay.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTest {
    @Test
    void ignoresForwardedAddressWhenNoProxyIsTrusted() {
        var request = request("198.51.100.20", "203.0.113.99");

        assertThat(new ClientAddressResolver("").resolve(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void ignoresForwardedAddressWhenSocketPeerIsNotInTrustedRanges() {
        var request = request("198.51.100.20", "203.0.113.99");

        assertThat(new ClientAddressResolver("10.20.0.0/16").resolve(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void walksForwardedChainFromNearestProxyAndIgnoresSpoofedPrefix() {
        var request = request("10.20.0.10", "203.0.113.99, 198.51.100.22, 10.20.0.9");

        assertThat(new ClientAddressResolver("10.20.0.0/16").resolve(request)).isEqualTo("198.51.100.22");
    }

    @Test
    void combinesRepeatedForwardingHeadersBeforeWalkingTheChain() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.20.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.99");
        request.addHeader("X-Forwarded-For", "198.51.100.22, 10.20.0.9");

        assertThat(new ClientAddressResolver("10.20.0.0/16").resolve(request)).isEqualTo("198.51.100.22");
    }

    @Test
    void returnsSocketPeerForInvalidOrOversizedForwardedChain() {
        var malformed = request("10.20.0.10", "client.example, 198.51.100.22");
        var oversized = request("10.20.0.10", "198.51.100.1, 198.51.100.2, 198.51.100.3, 198.51.100.4, 198.51.100.5, 198.51.100.6, 198.51.100.7, 198.51.100.8, 198.51.100.9, 198.51.100.10, 198.51.100.11");

        var resolver = new ClientAddressResolver("10.20.0.0/16");
        assertThat(resolver.resolve(malformed)).isEqualTo("10.20.0.10");
        assertThat(resolver.resolve(oversized)).isEqualTo("10.20.0.10");
    }

    @Test
    void supportsIpv6TrustedProxies() {
        var request = request("2001:db8:abcd::10", "2001:4860:4860::8888");

        assertThat(new ClientAddressResolver("2001:db8:abcd::/48").resolve(request)).isEqualTo("2001:4860:4860:0:0:0:0:8888");
    }

    @Test
    void rejectsMalformedTrustedProxyConfigurationAtStartup() {
        assertThatThrownBy(() -> new ClientAddressResolver("proxy.internal"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientAddressResolver("10.20.0.0/33"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientAddressResolver("0.0.0.0/0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientAddressResolver("::/0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MockHttpServletRequest request(String peer, String forwardedFor) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
