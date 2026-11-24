package com.flowpay.identity.application;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;

/** Resolves a client address without trusting forwarding headers from untrusted peers. */
@Component
public class ClientAddressResolver {
    private static final Pattern IPV4 = Pattern.compile("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]+");
    private static final int MAX_FORWARDED_HEADER_LENGTH = 2048;
    private static final int MAX_FORWARDED_HOPS = 10;
    private final List<Cidr> trustedProxies;

    public ClientAddressResolver(@Value("${flowpay.security.trusted-proxy-cidrs:}") String configuredCidrs) {
        this.trustedProxies = parseTrustedProxies(configuredCidrs);
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (peer == null || peer.isBlank()) return "unknown";
        byte[] peerAddress = parseAddress(peer);
        if (peerAddress == null) return peer;
        if (!isTrusted(peerAddress)) return format(peerAddress);

        List<byte[]> chain = parseForwardedChain(Collections.list(request.getHeaders("X-Forwarded-For")));
        if (chain == null || chain.isEmpty()) return peer;

        for (int index = chain.size() - 1; index >= 0; index--) {
            byte[] candidate = chain.get(index);
            if (!isTrusted(candidate)) return format(candidate);
        }
        return peer;
    }

    private boolean isTrusted(byte[] address) {
        return trustedProxies.stream().anyMatch(cidr -> cidr.contains(address));
    }

    private static List<Cidr> parseTrustedProxies(String configured) {
        if (configured == null || configured.isBlank()) return List.of();
        var result = new ArrayList<Cidr>();
        for (String value : configured.split(",", -1)) {
            String item = value.trim();
            if (item.isEmpty()) throw new IllegalArgumentException("FLOWPAY_TRUSTED_PROXY_CIDRS contains an empty entry.");
            result.add(parseCidr(item));
        }
        return List.copyOf(result);
    }

    private static Cidr parseCidr(String value) {
        String[] parts = value.split("/", -1);
        if (parts.length > 2) throw new IllegalArgumentException("Invalid trusted proxy CIDR.");
        byte[] address = parseAddress(parts[0]);
        if (address == null) throw new IllegalArgumentException("Invalid trusted proxy address.");
        int bits = address.length * Byte.SIZE;
        int prefix = bits;
        if (parts.length == 2) {
            try { prefix = Integer.parseInt(parts[1]); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid trusted proxy CIDR prefix.", invalid); }
        }
        if (prefix < 0 || prefix > bits) throw new IllegalArgumentException("Trusted proxy CIDR prefix is outside the address range.");
        int minimumPrefix = address.length == 4 ? 16 : 48;
        if (prefix < minimumPrefix) {
            throw new IllegalArgumentException("Trusted proxy CIDRs must be narrower than IPv4 /16 or IPv6 /48.");
        }
        return new Cidr(address, prefix);
    }

    private static List<byte[]> parseForwardedChain(List<String> headers) {
        if (headers.isEmpty()) return null;
        int totalLength = 0;
        var addresses = new ArrayList<byte[]>();
        for (String header : headers) {
            if (header == null || header.isBlank()) return null;
            totalLength += header.length();
            if (totalLength > MAX_FORWARDED_HEADER_LENGTH) return null;
            String[] values = header.split(",", -1);
            for (String value : values) {
                if (addresses.size() == MAX_FORWARDED_HOPS) return null;
                byte[] address = parseAddress(value.trim());
                if (address == null) return null;
                addresses.add(address);
            }
        }
        return addresses;
    }

    private static byte[] parseAddress(String value) {
        if (value == null || value.isBlank()) return null;
        if (IPV4.matcher(value).matches()) {
            String[] octets = value.split("\\.");
            byte[] result = new byte[4];
            for (int index = 0; index < octets.length; index++) {
                int octet;
                try { octet = Integer.parseInt(octets[index]); }
                catch (NumberFormatException invalid) { return null; }
                if (octet > 255) return null;
                result[index] = (byte) octet;
            }
            return result;
        }
        if (!value.contains(":") || !IPV6.matcher(value).matches()) return null;
        try {
            InetAddress address = InetAddress.getByName(value);
            return address instanceof Inet6Address ? address.getAddress() : null;
        } catch (UnknownHostException invalid) {
            return null;
        }
    }

    private static String format(byte[] address) {
        try { return InetAddress.getByAddress(address).getHostAddress(); }
        catch (UnknownHostException impossible) { throw new IllegalArgumentException("Unsupported client address.", impossible); }
    }

    private record Cidr(byte[] network, int prefixLength) {
        boolean contains(byte[] address) {
            if (network.length != address.length) return false;
            int fullBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            for (int index = 0; index < fullBytes; index++) {
                if (network[index] != address[index]) return false;
            }
            if (remainingBits == 0) return true;
            int mask = 0xff << (Byte.SIZE - remainingBits);
            return (network[fullBytes] & mask) == (address[fullBytes] & mask);
        }
    }
}
