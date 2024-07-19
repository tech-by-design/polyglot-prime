#!/bin/bash

# Get the value of SPRING_PROFILES_ACTIVE
profile=$(printenv SPRING_PROFILES_ACTIVE)

# Form the environment variable names
jdbc_url_var="${profile}_TECHBD_UDI_DS_PRIME_JDBC_URL"
jdbc_user_var="${profile}_TECHBD_UDI_DS_PRIME_JDBC_USERNAME"
jdbc_pass_var="${profile}_TECHBD_UDI_DS_PRIME_JDBC_PASSWORD"

# Get the values of the environment variables
jdbc_url=$(printenv $jdbc_url_var)
jdbc_user=$(printenv $jdbc_user_var)
jdbc_pass=$(printenv $jdbc_pass_var)

# Extract the dbhost and dbname from the JDBC URL
dbhost=$(echo $jdbc_url | sed -n 's|jdbc:postgresql://\([^/]*\)/.*|\1|p')
dbname=$(echo $jdbc_url | sed -n 's|jdbc:postgresql://[^/]*/\([^?]*\).*|\1|p')


# Run the SchemaSpy command
java -jar schemaspy-6.2.4.jar -t pgsql11 -db $dbname -u $jdbc_user -p $jdbc_pass -host $dbhost -dp postgresql-42.2.5.jar -schemas techbd_udi_ingress,techbd_orch_ctl,techbd_udi_assurance,techbd_udi_diagnostics -debug -o target/site/schemaSpy


# Start the Spring Boot application
exec mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 --server.host=0.0.0.0"
