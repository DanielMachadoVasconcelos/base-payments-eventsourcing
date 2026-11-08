FROM eclipse-temurin:25.0.2_10-jdk AS build
WORKDIR /workspace
COPY . .
ARG MODULE
RUN ./gradlew :${MODULE}:bootJar --no-daemon && cp ${MODULE}/build/libs/${MODULE}-1.0.0.jar /application.jar
FROM eclipse-temurin:25.0.2_10-jre
WORKDIR /app
COPY --from=build /application.jar /app/application.jar
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
