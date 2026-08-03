package cc.orbexa.hhy.boot.payment;

import cc.orbexa.hhy.access.admin.AdminIdempotencySnapshotCipher;
import cc.orbexa.hhy.commerce.R17PaymentContracts.PaymentResource;
import cc.orbexa.hhy.commerce.R17PaymentPostgresStore;
import cc.orbexa.hhy.commerce.R17PaymentService;
import cc.orbexa.hhy.commerce.R17PaymentStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class R17PaymentConfiguration {
    @Bean
    R17PaymentSignatureVerifier r17PaymentSignatureVerifier(
            @Value("${HHY_PAYMENT_NOTIFY_SECRETS:}") String configuredSecrets,
            Clock clock) {
        return new R17PaymentSignatureVerifier(configuredSecrets, clock);
    }

    @Bean
    R17PaymentStore.Codec r17PaymentCodec(
            ObjectMapper objectMapper, AdminIdempotencySnapshotCipher snapshots) {
        ObjectMapper canonical = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return new R17PaymentStore.Codec() {
            @Override
            public byte[] canonicalBytes(Object value) {
                return bytes(canonical, value);
            }

            @Override
            public String json(Object value) {
                return new String(bytes(canonical, value), StandardCharsets.UTF_8);
            }

            @Override
            public String encrypt(
                    String scope, String key, String requestHash, String type, byte[] plaintext) {
                return snapshots.encrypt(scope, key, requestHash, type, plaintext);
            }

            @Override
            public byte[] decrypt(
                    String scope, String key, String requestHash, String type, String envelope) {
                return snapshots.decrypt(scope, key, requestHash, type, envelope);
            }

            @Override
            public PaymentResource payment(byte[] json) {
                return read(canonical, json, PaymentResource.class);
            }

            @Override
            public Map<String, Object> map(String json) {
                return read(canonical, json, new TypeReference<>() { });
            }
        };
    }

    @Bean
    R17PaymentStore r17PaymentStore(
            DataSource dataSource, R17PaymentStore.Codec codec, Clock clock) {
        return new R17PaymentPostgresStore(dataSource, codec, clock);
    }

    @Bean
    R17PaymentService r17PaymentService(
            R17PaymentStore store,
            R17PaymentStore.Codec codec,
            @Value("${HHY_PAYMENT_ALLOWED_RETURN_HOSTS:h5.orbexa.cc,stg-h5.orbexa.cc}")
            String allowedHosts) {
        Set<String> hosts = Arrays.stream(allowedHosts.split(","))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (hosts.isEmpty()) throw new IllegalStateException("Payment return host whitelist is empty");
        return new R17PaymentService(store, codec, hosts);
    }

    private static byte[] bytes(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception failure) {
            throw new IllegalStateException("R17 JSON encode failed", failure);
        }
    }

    private static <T> T read(ObjectMapper mapper, byte[] json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception failure) {
            throw new IllegalStateException("R17 JSON decode failed", failure);
        }
    }

    private static <T> T read(ObjectMapper mapper, String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception failure) {
            throw new IllegalStateException("R17 JSON decode failed", failure);
        }
    }
}
