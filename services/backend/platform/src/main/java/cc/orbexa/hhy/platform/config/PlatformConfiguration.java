package cc.orbexa.hhy.platform.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HhyPlatformProperties.class)
public class PlatformConfiguration {
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
