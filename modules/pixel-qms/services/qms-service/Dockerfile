# pixel-qms (qms-service) — Railway 배포용 이미지
#
# 빌드 컨텍스트: **레포 루트**
#   이 모듈은 shared/(플랫폼 공통 코어)를 Gradle 복합 빌드로 참조하므로, 이미지 빌드에
#   모듈 디렉터리와 shared/가 모두 필요하다. Docker는 컨텍스트 밖(../)을 읽지 못한다.
#   Railway 설정 → Root Directory: /  ,  Dockerfile Path: 이 파일 경로
#
# 이미지 안에서도 레포와 같은 디렉터리 배치를 유지해야 한다.
# settings.gradle의 includeBuild('../../../../shared') 상대 경로가 그대로 성립해야 하기 때문.

FROM gradle:8.10-jdk17 AS build
WORKDIR /src
COPY shared ./shared
COPY modules/pixel-qms/services/qms-service ./modules/pixel-qms/services/qms-service
WORKDIR /src/modules/pixel-qms/services/qms-service
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
# *-SNAPSHOT.jar 만 매칭된다(-plain.jar 는 제외).
COPY --from=build /src/modules/pixel-qms/services/qms-service/build/libs/*-SNAPSHOT.jar app.jar
# Railway가 PORT를 주입하고, application.yml이 ${PORT:9004}로 받는다.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -jar app.jar"]
