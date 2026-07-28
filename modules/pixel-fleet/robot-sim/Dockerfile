# robot-sim — 가상 로봇 시뮬레이터 (웹 서버 없는 워커)
# 빌드 컨텍스트: 이 디렉터리(robot-sim)

FROM gradle:8.10-jdk17 AS build
WORKDIR /src
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/build/libs/*-SNAPSHOT.jar app.jar
# 포트를 열지 않는다(web-application-type: none). MQTT_BROKER_URL만 있으면 된다.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -jar app.jar"]
