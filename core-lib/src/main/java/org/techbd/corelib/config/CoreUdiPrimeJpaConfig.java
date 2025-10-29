package org.techbd.corelib.config;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@org.springframework.context.annotation.Configuration
@EnableTransactionManagement
public class CoreUdiPrimeJpaConfig {

    private static final Logger log = LoggerFactory.getLogger(CoreUdiPrimeJpaConfig.class);

    @Autowired
    private Environment environment;

    @Bean
    @Primary
    @Lazy
    @ConditionalOnProperty(name = "org.techbd.udi.prime.jdbc.url")
    @ConfigurationProperties(prefix = "org.techbd.udi.prime.jdbc")
    public DataSource udiPrimaryDataSource() {
        String jdbcUrl = environment.getProperty("org.techbd.udi.prime.jdbc.url");
        String username = environment.getProperty("org.techbd.udi.prime.jdbc.username");
        String driver = environment.getProperty("org.techbd.udi.prime.jdbc.driver-class-name");

        log.info("Initializing UDI Primary DataSource...");
        log.info("JDBC URL      : {}", jdbcUrl != null ? jdbcUrl : " MISSING");
        log.info("Username      : {}", username != null ? username : " MISSING");
        log.info("Driver        : {}", driver != null ? driver : " MISSING");
        String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (activeProfile == null || activeProfile.isBlank()) {
            log.warn("SPRING_PROFILES_ACTIVE is not set as an environment variable!");
        } else {
            log.info("Active Profile (from ENV): {}", activeProfile);
        }

        // Construct the expected variable name dynamically
        String jdbcEnvVarName = (activeProfile != null ? activeProfile.toUpperCase() : "DEFAULT")
                + "_TECHBD_UDI_DS_PRIME_JDBC_URL";
        String jdbcUrl1 = System.getenv(jdbcEnvVarName);

        log.info("Checking environment variable: {}", jdbcEnvVarName);
        DataSource ds = DataSourceBuilder.create().build();
        log.info("UDI Primary DataSource created: {}", ds.getClass().getName());
        
        return ds;
    }

    public record DataSourceHealthCheckResult(DataSource dataSrc, Exception error, Environment environment,
                                              String... expected) {
        public boolean isAlive() {
            return error == null;
        }

        public List<String> expectedConf() {
            return Configuration.checkProperties(environment, expected);
        }
    }

    public DataSourceHealthCheckResult udiPrimaryDataSrcHealth() {
        log.info("Running health check for UDI Primary DataSource");

        // Fetch the active Spring profile from the environment
        String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (activeProfile == null || activeProfile.isBlank()) {
            log.warn("SPRING_PROFILES_ACTIVE is not set as an environment variable!");
        } else {
            log.info("Active Profile (from ENV): {}", activeProfile);
        }

        // Construct the expected variable name dynamically
        String jdbcEnvVarName = (activeProfile != null ? activeProfile.toUpperCase() : "DEFAULT")
                + "_TECHBD_UDI_DS_PRIME_JDBC_URL";
        String jdbcUrl = System.getenv(jdbcEnvVarName);

        log.info("Checking environment variable: {}", jdbcEnvVarName);
        log.info("Resolved JDBC URL from ENV: {}", jdbcUrl != null ? jdbcUrl : "MISSING");

        final var ds = udiPrimaryDataSource();
        try (Connection connection = ds.getConnection()) {
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            log.info("DataSource connection successful (Catalog: {}, Schema: {}).", catalog, schema);

            return new DataSourceHealthCheckResult(ds, null, environment, jdbcEnvVarName);
        } catch (Exception e) {
            log.error("DataSource connection failed!");
            log.error("Reason: {}", e.getMessage(), e);
            log.error("Environment variable used: {}", jdbcEnvVarName);
            return new DataSourceHealthCheckResult(null, e, environment, jdbcEnvVarName);
        }
    }

    @Bean
    public DSLContext dsl() {
        log.info("Initializing DSLContext...");
        DSLContext context = new DefaultDSLContext(configuration());
        log.info("DSLContext created successfully with SQL dialect: {}", SQLDialect.POSTGRES);
        return context;
    }

    public org.jooq.Configuration configuration() {
        log.info("Setting up JOOQ Configuration...");
        final var jooqConfiguration = new DefaultConfiguration();
        var provider = connectionProvider();
        jooqConfiguration.set(provider);
        jooqConfiguration.setSQLDialect(SQLDialect.POSTGRES);

        log.info("JOOQ Configuration set with dialect: {} and connectionProvider: {}",
                SQLDialect.POSTGRES, provider.getClass().getSimpleName());

        return jooqConfiguration;
    }

    @Bean
    public DataSourceConnectionProvider connectionProvider() {
        return new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(udiPrimaryDataSource()));
    }
}
