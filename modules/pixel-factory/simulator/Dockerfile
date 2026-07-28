# factory-sim — 가공 설비 시뮬레이터 (웹 서버 없는 워커)
# 빌드 컨텍스트: 이 디렉터리(simulator)
#
# robot-sim과 달리 Spring Boot가 아니라 application 플러그인을 쓰므로
# installDist가 만든 실행 스크립트로 띄운다.

FROM gradle:8.10-jdk17 AS build
WORKDIR /src
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/build/install/simulator ./
# 포트를 열지 않는다. MQTT_URL / SIM_SPEED 환경변수만 받는다.
ENTRYPOINT ["./bin/simulator"]
