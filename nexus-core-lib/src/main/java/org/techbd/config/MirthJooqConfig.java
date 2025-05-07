package org.techbd.config;

import java.sql.Connection;
import java.sql.SQLException;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;

import com.zaxxer.hikari.HikariDataSource; // ✅ Use HikariCP instead of Spring Boot's proxy

public class MirthJooqConfig {

    private static final String DB_URL = System.getProperty("org.techbd.udi.prime.jdbc.url", 
        "jdbc:postgresql://ep-old-mouse-a5zwc3ri.us-east-2.aws.neon.tech/neondb");
    private static final String DB_USER = System.getProperty("org.techbd.udi.prime.jdbc.username", "neondb_owner");
    private static final String DB_PASSWORD = System.getProperty("org.techbd.udi.prime.jdbc.password", "Zh7vilpW9fMu");

    private static HikariDataSource dataSource; // ✅ Use HikariCP

    /**
     * Creates and returns a DSLContext for JOOQ.
     */
    public static DSLContext dsl() {
        if (dataSource == null) {
            dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(DB_URL);
            dataSource.setUsername(DB_USER);
            dataSource.setPassword(DB_PASSWORD);
            dataSource.setMaximumPoolSize(20);
            dataSource.setMinimumIdle(5);
            dataSource.setDriverClassName("org.postgresql.Driver");
            
            // Connection timeout settings
            dataSource.setConnectionTimeout(10000); // 10 seconds
            dataSource.setIdleTimeout(300000); // 5 minutes
            dataSource.setMaxLifetime(600000); // 10 minutes
            
            // Enable connection testing
            dataSource.setConnectionTestQuery("SELECT 1");
            dataSource.setValidationTimeout(5000); // 5 seconds
        }

        var jooqConfiguration = new DefaultConfiguration();
        jooqConfiguration.set(new DataSourceConnectionProvider(dataSource));
        jooqConfiguration.setSQLDialect(SQLDialect.POSTGRES);

        return new DefaultDSLContext(jooqConfiguration);
    }

    /**
     * Checks if the database connection is alive.
     */
    public static boolean isDatabaseAlive() {
        try (Connection conn = dataSource.getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
