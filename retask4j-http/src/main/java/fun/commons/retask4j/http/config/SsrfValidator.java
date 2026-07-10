package fun.commons.retask4j.http.config;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

public final class SsrfValidator {

    private static boolean isIpv4MappedIpv6(InetAddress address) {
        if (address instanceof java.net.Inet6Address ipv6) {
            byte[] bytes = ipv6.getAddress();
            // IPv4-mapped IPv6: ::ffff:x.x.x.x — first 10 bytes are 0, bytes 10-11 are 0xff
            boolean prefixZero = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) { prefixZero = false; break; }
            }
            if (prefixZero && bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff) {
                return true;
            }
            // IPv4-compatible IPv6: ::x.x.x.x — first 12 bytes are 0 (deprecated but still exploitable)
            boolean allZero = true;
            for (int i = 0; i < 12; i++) {
                if (bytes[i] != 0) { allZero = false; break; }
            }
            if (allZero) return true;
        }
        return false;
    }

    private static boolean isIpv6UniqueLocal(InetAddress address) {
        if (address instanceof java.net.Inet6Address ipv6) {
            byte[] bytes = ipv6.getAddress();
            // IPv6 unique-local: fc00::/7 — first byte's top 7 bits are 0xfc or 0xfd
            return (bytes[0] & 0xfe) == 0xfc;
        }
        return false;
    }

    private static boolean isSpecialUseAddress(InetAddress address) {
        if (address instanceof java.net.Inet4Address ipv4) {
            byte[] bytes = ipv4.getAddress();
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            // 100.64.0.0/10 — Shared Address Space (CGNAT / AWS VPC endpoints)
            if (first == 100 && (second & 0xc0) == 0x40) return true;
            // 198.18.0.0/15 — Benchmarking
            if (first == 198 && (second & 0xfe) == 18) return true;
            // 192.0.2.0/24 — TEST-NET-1 (documentation)
            if (first == 192 && second == 0 && (bytes[2] & 0xff) == 2) return true;
            // 198.51.100.0/24 — TEST-NET-2 (documentation)
            if (first == 198 && second == 51 && (bytes[2] & 0xff) == 100) return true;
            // 203.0.113.0/24 — TEST-NET-3 (documentation)
            if (first == 203 && second == 0 && (bytes[2] & 0xff) == 113) return true;
        }
        return false;
    }

    private SsrfValidator() {}

    public static void validateUri(String url, String label) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(label + " is not a valid URL: " + e.getMessage());
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException(label + " must use http or https scheme");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(label + " must have a valid host");
        }
        validateHost(host, label);
    }

    public static void validateHost(String host, String label) {
        resolveAndValidate(host, label);
    }

    /**
     * Resolves the hostname, validates that all resolved IPs are non-private,
     * and returns the first validated IP address string.
     * Callers should use the returned IP directly in HTTP connections (with the original
     * hostname as the Host header) to prevent DNS rebinding attacks.
     */
    public static String resolveAndValidate(String host, String label) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw new IllegalArgumentException("No addresses resolved for " + label + ": " + host);
            }
            for (InetAddress address : addresses) {
                // Check the address itself
                if (isAddressPrivate(address)) {
                    throw new IllegalArgumentException(label + " targets a private/reserved address");
                }
                // For IPv4-mapped/compatible IPv6, also check the embedded IPv4
                if (isIpv4MappedIpv6(address)) {
                    byte[] bytes = address.getAddress();
                    byte[] ipv4Bytes = new byte[4];
                    System.arraycopy(bytes, 12, ipv4Bytes, 0, 4);
                    try {
                        InetAddress embeddedIpv4 = InetAddress.getByAddress(ipv4Bytes);
                        if (isAddressPrivate(embeddedIpv4)) {
                            throw new IllegalArgumentException(label + " targets a private/reserved address via IPv4-mapped IPv6");
                        }
                    } catch (UnknownHostException e) {
                        throw new IllegalArgumentException(label + " contains invalid embedded IPv4 address");
                    }
                }
            }
            return addresses[0].getHostAddress();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve host for " + label + ": " + host);
        }
    }

    private static boolean isAddressPrivate(InetAddress address) {
        return address.isLoopbackAddress() || address.isLinkLocalAddress() ||
            address.isSiteLocalAddress() || address.isAnyLocalAddress() ||
            address.isMulticastAddress() ||
            isIpv6UniqueLocal(address) || isSpecialUseAddress(address);
    }
}
