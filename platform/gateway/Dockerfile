# gateway + dashboard — 플랫폼의 유일한 퍼블릭 서비스
#
# 대시보드 정적 파일을 게이트웨이 이미지에 함께 넣어 **같은 오리진**으로 서빙한다.
#   - 퍼블릭 서비스가 하나로 줄고
#   - 브라우저 입장에서 API(/api/**)와 화면이 같은 오리진이라 CORS가 아예 없다.
#
# 빌드 컨텍스트: platform/  (dashboard와 gateway를 모두 담아야 하므로)
#   Railway 설정 → Root Directory: platform , Dockerfile Path: gateway/Dockerfile

# 1) 대시보드 정적 빌드
FROM node:20-alpine AS dashboard
WORKDIR /dash
COPY dashboard/package.json dashboard/package-lock.json ./
RUN npm ci
COPY dashboard/ ./
RUN npm run build

# 2) 게이트웨이 빌드 — 대시보드 산출물을 static 리소스로 포함시킨다
FROM gradle:8.10-jdk17 AS build
WORKDIR /src
COPY gateway/build.gradle gateway/settings.gradle ./
COPY gateway/src ./src
COPY --from=dashboard /dash/dist ./src/main/resources/static
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/build/libs/*-SNAPSHOT.jar app.jar
# Railway가 PORT를 주입하고, application.yml이 ${PORT:9000}으로 받는다.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -jar app.jar"]
