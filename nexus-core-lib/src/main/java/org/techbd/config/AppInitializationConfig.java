package org.techbd.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "org.techbd")
@EnableConfigurationProperties(CoreAppConfig.class)
public class AppInitializationConfig {

}
