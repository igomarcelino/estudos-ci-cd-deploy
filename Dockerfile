# 1-> build
FROM maven:3.8-openjdk-17 AS builder
WORKDIR /app

# -> copiar o pom com as dependencias

COPY pom.xml .
RUN mvn dependency:go-offline

# -> copiar o codigo gerado

COPY src ./src

RUN mvn clean package -DskipTests

# 2 -> Runtime

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copiar o Jar gerado

COPY --from=builder /app/target/app.jar app.jar

# expor a porta do spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]