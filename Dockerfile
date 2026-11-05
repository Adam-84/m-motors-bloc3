FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci --ignore-scripts
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /app/backend/src/main/resources/static ./src/main/resources/static
RUN mvn -B -ntp verify

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S motors && adduser -S motors -G motors && mkdir /app && chown motors:motors /app
WORKDIR /app
COPY --from=backend /app/backend/target/m-motors-1.0.0.jar ./app.jar
USER motors
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
