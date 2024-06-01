package org.techbd.udi;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;

@Configuration
@EnableJpaRepositories(basePackages = "org.techbd.udi")
@EnableTransactionManagement
public class UdiPrimeJpaConfig {

    @Autowired
    private JpaProperties jpaProperties;

    @Autowired
    private Environment environment;

    @Bean
    @Primary
    @Lazy
    @ConditionalOnProperty(name = "org.techbd.udi.prime.jdbc.url")
    @ConfigurationProperties(prefix = "org.techbd.udi.prime.jdbc")
    public DataSource udiPrimaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    public record DataSourceHealthCheckResult(DataSource dataSrc, Exception error, Environment environment,
            String... expected) {
        public boolean isAlive() {
            return error == null;
        }

        public List<String> expectedConf() {
            return org.techbd.conf.Configuration.checkProperties(environment, expected);
        }
    }

    public DataSourceHealthCheckResult udiPrimaryDataSrcHealth() {
        final var ds = udiPrimaryDataSource();
        try (Connection connection = ds.getConnection()) {
            return new DataSourceHealthCheckResult(ds, null, environment,
                    "${${SPRING_PROFILES_ACTIVE}_TECHBD_UDI_DS_PRIME_JDBC_URL:}");
        } catch (Exception e) {
            return new DataSourceHealthCheckResult(null, e, environment,
                    "${${SPRING_PROFILES_ACTIVE}_TECHBD_UDI_DS_PRIME_JDBC_URL:}");
        }
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder builder,
            DataSource udiPrimaryDataSource) {
        return builder
                .dataSource(udiPrimaryDataSource)
                .properties(jpaProperties.getProperties())
                .packages("org.techbd.udi.entity")
                .persistenceUnit("default")
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
