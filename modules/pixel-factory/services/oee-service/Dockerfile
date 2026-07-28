# pixel-factory (oee-service) — Railway 배포용 이미지
# 빌드 컨텍스트: 이 디렉터리(services/oee-service)

FROM gradle:8.10-jdk17 AS build
WORKDIR /src
COPY build.gradle settings.gradle ./
COPY src ./src
# 래퍼 대신 이미지의 gradle을 쓴다(래퍼 다운로드 왕복 제거).
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
# *-SNAPSHOT.jar 만 매칭된다(-plain.jar 는 제외).
COPY --from=build /src/build/libs/*-SNAPSHOT.jar app.jar
# Railway가 PORT를 주입하고, application.yml이 ${PORT:9001}로 받는다.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -jar app.jar"]
