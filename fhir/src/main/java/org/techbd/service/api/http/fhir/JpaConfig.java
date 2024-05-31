package org.techbd.service.api.http.fhir;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(basePackages = "org.techbd.service.api.http.fhir.repository")
@EnableTransactionManagement
public class JpaConfig {

  @Autowired
  private DataSource dataSource;

  @Autowired
  private JpaProperties jpaProperties;

  @Bean
  @Primary
  public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder builder) {
    return builder
        .dataSource(dataSource)
        .properties(jpaProperties.getProperties())
        .packages("org.techbd.service.api.http.fhir.entity")
        .persistenceUnit("default")
        .build();
  }

  @Bean
  @Primary
  public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
    return new JpaTransactionManager(entityManagerFactory);
  }
}
