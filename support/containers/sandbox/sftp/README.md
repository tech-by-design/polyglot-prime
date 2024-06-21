# SFTP and Workflow Processing Servers Setup

This guide covers the setup for a mechanism to SFTP files, process them, store
output in a postgres database and view outputs using sql page using Docker and
docker compose.

The architecture involves the following Docker containers:

- SFTP container
- Workflow container (Deno, DuckDB, and SQLite)
- Postgres container
- Sql Page (for sqllite) container
- Sql Page (for postgres) container

These containers will mount to the local filesystem for a closer emulation to
our current production environment and to support persistent data across local
deployments.

## Docker Image & Docker Compose Information

- `Dockerfile.workflow`: Builds the Docker image for the processing server with
  Deno, DuckDB, and SQLite.
- `atmoz/sftp`: prebuilt image to support SFTP
- `lovasoa/sqlpage`: prebuilt image to support sqlpage
- `postgres:13`: prebuilt postgres 13 image
- `docker-compose.yml`: Builds and orchestrates the running of the docker images

## Setup Instructions

### On a Windows/ Linux Local Machine (Mac not supported)

1. **Install Docker Desktop**: Ensure Docker Desktop is installed and running on
   your computer. Download it from the official Docker website.
2. **Clone the repository**: Clone or download the repository containing the
   Dockerfiles and `docker-compose.yml`.
3. **Create .env.local file**: create a file called `.env.local` (you can copy
   the existing .env file) and replace the directory mount locations. Once
   creating a `.env.local` file, it is recommended you append `.` in front of
   each file path in the `env` file you will create all necessary directories
   within this repository inside of `support/infrastructure/containers/mnt`
4. **Open a terminal**: Run the following commands from the project's root
   directory:

- `cd support/infrastructure/containers`
- `docker-compose --env-file .env.local up --build`

5. **Build and Run**: Execute `docker-compose --env-file .env.local up --build`
   to build the images and start the containers.
6. **Testing** (use any SFTP client): After docker compose starts, run
   `scp fixtures-sftp-simulator/* qe1@localhost:/ingress`. You can then
   view logs in the container at `/var/log/qe1.log`. You can also open an SFTP
   connection with the container using either `sftp qe1@localhost:/` or
   your SFTP viewer of choice just ensure it is configured to open the
   connection on port 2222.

### On a Virtual Machine (VM)

1. **Install Docker**: Ensure Docker and Docker Compose are installed on the VM.
   Use the official Docker installation guide for the VM's operating system.
2. **Transfer files**: Copy the Dockerfiles and `docker-compose.yml` to the VM.
3. **SSH into the VM**: Use an SSH client to connect to your VM.
4. **Build and Run**: Navigate to the directory containing the files and run
   `docker-compose up --build`.

## Below containerized is a WIP and not to be considered working currently.

### In an AWS Environment with ECS or Fargate

1. **Prepare the Docker Images**:

   - Build the Docker images locally or in a CI/CD pipeline.
   - Push the images to Amazon ECR (Elastic Container Registry). Use
     `aws ecr create-repository` to create repositories for each image if
     needed.

2. **Create an ECS Task Definition**:

   - Go to the ECS console and create a new task definition.
   - For each container, specify the image URL from ECR, memory and CPU
     requirements, and set the volume mount points. Use `/home` for the SFTP
     server and `/data` for the processing server.

3. **Create an ECS Cluster**:

   - Choose the Fargate launch type if you prefer serverless or EC2 if you want
     more control over the hosting environment.
   - Follow the prompts to configure networking and security settings.

4. **Run the Task**:

   - Once the cluster is set up, you can run the task by specifying the task
     definition and desired count.
   - Configure the task's network settings to ensure the SFTP port (22) is
     accessible.

5. **Access and Operate**:
   - Use the public IP or domain name (if configured) to access the SFTP server.
   - Monitor logs and performance directly from the ECS console or CloudWatch.

## Notes

- Ensure to configure security settings, such as SSH keys and networking rules,
  according to your environment's requirements.
- For AWS ECS, you might need to adjust IAM roles and policies to allow ECS
  tasks to access ECR images and other AWS resources.