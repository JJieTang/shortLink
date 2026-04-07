FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY .mvn ./.mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
