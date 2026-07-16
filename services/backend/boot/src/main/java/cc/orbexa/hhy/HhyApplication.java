package cc.orbexa.hhy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HhyApplication {
    public static void main(String[] args) { SpringApplication.run(HhyApplication.class, args); }
}
