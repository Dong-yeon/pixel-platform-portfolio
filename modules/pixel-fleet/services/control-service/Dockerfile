# pixel-fleet (control-service) — Railway 배포용 이미지
# 빌드 컨텍스트: 이 디렉터리(services/control-service)

FROM gradle:8.10-jdk17 AS build
WORKDIR /src
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/build/libs/*-SNAPSHOT.jar app.jar
# Railway가 PORT를 주입하고, application.yml이 ${PORT:9002}로 받는다.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -jar app.jar"]
