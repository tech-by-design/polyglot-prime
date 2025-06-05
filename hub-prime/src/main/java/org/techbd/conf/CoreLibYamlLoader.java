package org.techbd.conf;

import java.io.IOException;

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

            String[] profiles = env.getActiveProfiles();
            if (profiles.length == 0) {
                profiles = new String[]{"devl"}; // fallback default
            }

            for (String profile : profiles) {
                Resource profileYaml = new ClassPathResource("nexus-core-lib/application-" + profile + ".yml");
                if (profileYaml.exists()) {
                    for (PropertySource<?> ps : loader.load("nexus-core-lib-" + profile, profileYaml)) {
                        env.getPropertySources().addLast(ps);
                    }
                }
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