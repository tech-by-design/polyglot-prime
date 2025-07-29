/**
 * {@link org.springframework.boot.env.EnvironmentPostProcessor} implementation that loads additional
 * YAML configuration files from the classpath location {@code nexus-core-lib/application.yml} and,
 * if present, {@code nexus-core-lib/application-{profile}.yml} based on the active Spring profile.
 *
 * <p>
 * This allows core library configuration to be injected into the application's environment
 * before the Spring context is fully initialized, enabling shared configuration across
 * multiple services or modules.
 * </p>
 *
 * <p>It loads:</p>
 * <ul>
 *   <li>{@code nexus-core-lib/application.yml}</li>
 *   <li>{@code nexus-core-lib/application-{profile}.yml} if {@code SPRING_PROFILES_ACTIVE} is set</li>
 * </ul>
 *
 * <p>The profile-specific file is loaded with higher priority so it can override base settings.</p>
 */
package org.techbd.conf;

import java.io.IOException;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public class CoreLibYamlLoader implements EnvironmentPostProcessor, Ordered {
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        try {
            Resource base = new ClassPathResource("nexus-core-lib/application.yml");
            if (base.exists()) {
                for (PropertySource<?> ps : loader.load("nexus-core-lib", base)) {
                    env.getPropertySources().addLast(ps);
                }
            }            
            String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
            if (activeProfile != null && !activeProfile.isEmpty()) {
                List<PropertySource<?>> profileProps = loader.load(
                    "nexus-core-lib/application-" + activeProfile + ".yml",
                    new ClassPathResource("nexus-core-lib/application-" + activeProfile + ".yml")
                );

                for (PropertySource<?> ps : profileProps) {
                    env.getPropertySources().addFirst(ps);
                }
                env.setActiveProfiles(activeProfile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not load nexus-core-lib YAML", e);
        }
    }
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}