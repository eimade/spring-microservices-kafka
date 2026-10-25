# Image d'un microservice : docker build --build-arg MODULE=referentiel .
FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN chmod +x mvnw && ./mvnw -B -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre
ARG MODULE
RUN useradd --system --uid 10001 app
USER 10001
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
