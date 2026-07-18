package cc.orbexa.hhy.access.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminSecurityProperties.class)
public class AdminAccessConfiguration {
    @Bean
    PasswordEncoder adminPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean("adminSecurityObjectMapper")
    @Qualifier("adminSecurityObjectMapper")
    ObjectMapper adminSecurityObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean(ProviderConnectionTestCoordinator.SecretResolver.class)
    ProviderConnectionTestCoordinator.SecretResolver unavailableProviderSecretResolver() {
        return reference -> { throw new IllegalStateException("Provider secret resolver is unavailable"); };
    }

    @Bean
    ProviderConnectionTestCoordinator providerConnectionTestCoordinator(
            ObjectProvider<ProviderConnectorAdapters.ProviderProbeTransport> transports,
            ProviderConnectionTestCoordinator.SecretResolver secrets,
            Clock clock) {
        ProviderConnectorAdapters.ProviderProbeTransport transport = transports.getIfAvailable();
        List<ProviderConnectionTestCoordinator.ProviderConnector> connectors = transport == null
                ? List.of()
                : List.of(
                        ProviderConnectorAdapters.sms(transport),
                        ProviderConnectorAdapters.storage(transport),
                        ProviderConnectorAdapters.identity(transport),
                        ProviderConnectorAdapters.payment(transport),
                        ProviderConnectorAdapters.payout(transport));
        return new ProviderConnectionTestCoordinator(connectors, secrets, clock);
    }

    @Bean
    ProviderConfigVersionService providerConfigVersionService(
            ProviderConfigValidator validator,
            ProviderConnectionTestCoordinator connectionTests,
            R03ProviderConfigPostgresStore store,
            Clock clock) {
        return new ProviderConfigVersionService(validator, connectionTests, store, store, clock);
    }
}
