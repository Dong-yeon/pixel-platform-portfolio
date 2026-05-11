# PixelFactory Backend

PixelFactory MVP의 Spring Boot 3 기반 백엔드 초기 구성입니다.

## Stack

- Java 17
- Spring Boot 3
- Gradle
- PostgreSQL
- Spring Security
- JWT
- Spring Data JPA
- QueryDSL
- Swagger/OpenAPI

## Run PostgreSQL

```bash
docker compose up -d postgres
```

## Run Backend

Gradle Wrapper로 실행합니다.

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

Health Check:

```text
GET http://localhost:8080/api/health
```

Mock Login:

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "operator",
  "password": "password"
}
```

## Mock Users

현재 로그인은 MVP 초기 Mock입니다.

- `admin` → `ADMIN`
- `qms` → `QMS_MANAGER`
- `inspector` → `INSPECTOR`
- `warehouse` → `WAREHOUSE_OPERATOR`
- 그 외 → `OPERATOR`

TODO: 다음 단계에서 실제 UserRepository + PasswordEncoder 기반 인증으로 교체합니다.
