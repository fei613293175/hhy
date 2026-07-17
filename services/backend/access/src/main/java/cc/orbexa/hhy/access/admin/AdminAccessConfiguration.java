package cc.orbexa.hhy.access.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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

    @Bean
    ObjectMapper adminSecurityObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
