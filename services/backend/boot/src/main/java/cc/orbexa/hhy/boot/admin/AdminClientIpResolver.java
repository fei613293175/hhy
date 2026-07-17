package cc.orbexa.hhy.boot.admin;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

@Component
public final class AdminClientIpResolver {
    private static final int MAX_FORWARDED_LENGTH = 1024;
    private static final int MAX_FORWARD_HOPS = 16;
    private final List<IpAddressMatcher> trustedProxies;

    public AdminClientIpResolver(
            @Value("${hhy.admin-security.trusted-proxy-cidrs:127.0.0.1/32,::1/128}") String cidrs) {
        this.trustedProxies = parseCidrs(cidrs);
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (remote == null || !trusted(remote)) return remote == null ? "unknown" : remote;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank() || forwarded.length() > MAX_FORWARDED_LENGTH) {
            return remote;
        }
        String[] rawHops = forwarded.split(",", -1);
        if (rawHops.length > MAX_FORWARD_HOPS) return remote;
        List<String> hops = new ArrayList<>(rawHops.length);
        for (String raw : rawHops) {
            String hop = normalize(raw.strip());
            if (hop == null) return remote;
            hops.add(hop);
        }
        for (int index = hops.size() - 1; index >= 0; index--) {
            String hop = hops.get(index);
            if (!trusted(hop)) return hop;
        }
        return hops.isEmpty() ? remote : hops.getFirst();
    }

    private boolean trusted(String address) {
        return trustedProxies.stream().anyMatch(matcher -> matcher.matches(address));
    }

    private static List<IpAddressMatcher> parseCidrs(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<IpAddressMatcher> result = new ArrayList<>();
        for (String raw : value.split(",")) {
            String cidr = raw.strip();
            if (!cidr.isEmpty()) result.add(new IpAddressMatcher(cidr));
        }
        return List.copyOf(result);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank() || !value.matches("^[0-9A-Fa-f:.]{2,64}$")) return null;
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (Exception exception) {
            return null;
        }
    }
}
