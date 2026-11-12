# Giai đoạn 1: build file jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Giai đoạn 2: chạy
FROM eclipse-temurin:17-jre-noble
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends postgresql-client-16 util-linux tar gzip \
    fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
COPY scripts/backup.sh scripts/restore.sh /app/scripts/
RUN chmod 750 /app/scripts/backup.sh /app/scripts/restore.sh
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
