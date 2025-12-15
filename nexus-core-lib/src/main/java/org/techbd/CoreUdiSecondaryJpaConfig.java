package org.techbd;

import javax.sql.DataSource;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
@EnableTransactionManagement
public class CoreUdiSecondaryJpaConfig {
    @Autowired
    private Environment environment;

    @Bean(name = "udiSecondaryDataSource")
    @ConditionalOnProperty(name = "org.techbd.udi.secondary.jdbc.url")
    public DataSource udiSecondaryDataSource() {
        String jdbcUrl = environment.getProperty("org.techbd.udi.secondary.jdbc.url");
        String username = environment.getProperty("org.techbd.udi.secondary.jdbc.username");
        String password = environment.getProperty("org.techbd.udi.secondary.jdbc.password");
        String driverClassName = environment.getProperty("org.techbd.udi.secondary.jdbc.driverClassName",
                "org.postgresql.Driver");

        Integer maximumPoolSize = environment.getProperty("org.techbd.udi.secondary.jdbc.maximumPoolSize",
                Integer.class, 10);
        Integer minimumIdle = environment.getProperty("org.techbd.udi.secondary.jdbc.minimumIdle", Integer.class, 5);
        Long idleTimeout = environment.getProperty("org.techbd.udi.secondary.jdbc.idleTimeout", Long.class, 60000L);
        Long connectionTimeout = environment.getProperty("org.techbd.udi.secondary.jdbc.connectionTimeout", Long.class,
                20000L);
        Long maxLifetime = environment.getProperty("org.techbd.udi.secondary.jdbc.maxLifetime", Long.class, 1800000L);
 
        // Validation
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Secondary database jdbcUrl is not configured! Property: org.techbd.udi.secondary.jdbc.url");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Secondary database username is not configured! Property: org.techbd.udi.secondary.jdbc.username");
        }
        if (password == null) {
            throw new IllegalStateException(
                    "Secondary database password is not configured! Property: org.techbd.udi.secondary.jdbc.password");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setIdleTimeout(idleTimeout);
        config.setConnectionTimeout(connectionTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setPoolName("SecondaryHikariPool");

        return new HikariDataSource(config);
    }

    @Bean(name = "secondaryDslContext")
    public DSLContext dsl() {
        return new DefaultDSLContext(configuration());
    }

    @Bean(name = "secondaryJooqConfiguration")
    public org.jooq.Configuration configuration() {
        final var jooqConfiguration = new DefaultConfiguration();
        jooqConfiguration.set(secondaryConnectionProvider());
        jooqConfiguration.setSQLDialect(SQLDialect.POSTGRES);
        return jooqConfiguration;
    }

    @Bean(name = "secondaryConnectionProvider")
    public DataSourceConnectionProvider secondaryConnectionProvider() {
        return new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(udiSecondaryDataSource()));
    }
}