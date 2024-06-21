# Use the atmoz/sftp image as the base
FROM atmoz/sftp

ARG QE_NAMES
ARG TAG
ARG DATE 
ARG ORCHCTL_CRON
ARG SEMAPHORE
ARG DEPLOYMENT_DOMAIN

ENV QE_NAMES=${QE_NAMES}
ENV TAG=${TAG}
ENV DATE=${DATE}
ENV ORCHCTL_CRON=${ORCHCTL_CRON}
ENV SEMAPHORE=${SEMAPHORE}
ENV DEPLOYMENT_DOMAIN=${DEPLOYMENT_DOMAIN}

# Install wget
RUN apt-get update && \
    apt-get install -y wget && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install netcat for health checking purposes
RUN wget http://ftp.us.debian.org/debian/pool/main/n/netcat-openbsd/netcat-openbsd_1.217-3_amd64.deb && \
    dpkg -i netcat-openbsd_1.217-3_amd64.deb

# add the users conf file
COPY users.conf /etc/sftp/users.conf

# Add the README template
COPY README-template.md /README-template.md

RUN echo "Debug: QE_NAMES='$QE_NAMES', TAG='$TAG', DATE='$DATE', ORCHCTL_CRON='$ORCHCTL_CRON', DEPLOYMENT_DOMAIN='$DEPLOYMENT_DOMAIN', SEMAPHORE='$SEMAPHORE'"

# Ensure that QE_NAMES, version, date, and ORCHCTL_CRON variables are provided
# Use bash to execute the command
RUN /bin/bash -c "if [[ -z \"$QE_NAMES\" || -z \"$TAG\" || -z \"$DATE\" || -z \"$ORCHCTL_CRON\" || -z \"$DEPLOYMENT_DOMAIN\" || -z \"$SEMAPHORE\" ]]; then \
        echo \"Environment variables QE_NAMES, TAG, DATE, DEPLOYMENT_DOMAIN, SEMAPHORE, ORCHCTL_CRON must be set.\"; \
        exit 1; \
    fi"

# Iterate over the QE_NAMES, treating it as a space-separated list 
RUN /bin/bash -s "IFS=' ' read -r -a qe_names_array <<< "$QE_NAMES";"

# check if ORCHCTL_CRON has a slash in it, format in a way that is friendly to sed
RUN /bin/bash -c "if [[ \"$ORCHCTL_CRON\" == */* ]]; then \
        ORCHCTL_CRON=\$(echo \"$ORCHCTL_CRON\" | sed 's/\\//\\\\\\//g'); \
    fi"

RUN /bin/bash -c "\
    for qe_name in \${qe_names_array[@]}; do \
        output_dir=\"/home/\$qe_name\"; \
        mkdir -p \"\$output_dir\"; \
        rm -rf \"\$output_dir/ingress\"; \
        rm -rf \"\$output_dir/README.md\"; \
        sed 's/\${SEMAPHORE}/'\"\$SEMAPHORE\"'/g; \
             s/\${DEPLOYMENT_DOMAIN}/'\"\$DEPLOYMENT_DOMAIN\"'/g; \
             s/\${QE_NAME}/'\"\$qe_name\"'/g; \
             s/\${TAG}/'\"\$TAG\"'/g; \
             s/\${DATE}/'\"\$DATE\"'/g; \
             s/\${ORCHCTL_CRON}/'\"\$ORCHCTL_CRON\"'/g' \
             /README-template.md > \"\$output_dir/README.md\"; \
    done"
    

RUN echo "README files have been created."

# Add the health check script
COPY health-check.sh /health-check.sh
RUN chmod +x /health-check.sh

# Add the start script
COPY start-services.sh /usr/local/bin/start-services.sh
RUN chmod +x /usr/local/bin/start-services.sh

# expose the health check port
EXPOSE 8081
# expose the sftp port
EXPOSE 22

ENTRYPOINT ["/usr/local/bin/start-services.sh"]

