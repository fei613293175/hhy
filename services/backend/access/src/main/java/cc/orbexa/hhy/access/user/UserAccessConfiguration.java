package cc.orbexa.hhy.access.user;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UserAuthProperties.class)
public class UserAccessConfiguration { }
