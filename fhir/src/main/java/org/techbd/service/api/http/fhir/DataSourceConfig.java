package org.techbd.service.api.http.fhir;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
  public class DataSourceConfig {
    @Value("${admin.observe.neon.datasource.driver-class-name}")
    private String className;
    @Value("${admin.observe.neon.datasource.url}")
    private String url;
    @Value("${admin.observe.neon.datasource.username}")
    private String userName;
    @Value("${admin.observe.neon..datasource.password}")
    private String password;
  @Bean
  public DataSource dataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName(className);
    dataSource.setUrl(url);
    dataSource.setUsername(userName);
    dataSource.setPassword(password);
    return dataSource;
  }
}