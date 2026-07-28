# shared — 플랫폼 공통 코어

산업 도메인에 무관한 코드. 모듈들이 **Gradle 복합 빌드(`includeBuild`)**로 가져다 쓴다.
패키지는 `com.pixelplatform.core`.

| 패키지 | 내용 |
|---|---|
| `common` | 응답 래퍼(`ApiResponse`/`ErrorResponse`), 예외(`BusinessException`/`ErrorCode`/`GlobalExceptionHandler`), `BaseEntity`, OpenAPI·QueryDSL 설정 |
| `auth` | JWT(발급·검증·필터), 로그인 API, REST 인증/인가 진입점 |
| `user` | `User` 엔티티, `UserRole`, `UserStatus`, `UserRepository` |

## 모듈에 남는 것

| 파일 | 모듈에 남는 이유 |
|---|---|
| `SecurityConfig` | 허용 경로가 다르다 (fleet만 `/ws/**` 필요) |
| `UserDataInitializer` | 시드 계정·부서가 모듈마다 다르다 |

`UserRole`은 공유하되 플랫폼 전체 역할의 합집합으로 둔다
(ADMIN·OPERATOR·INSPECTOR·DISPATCHER). `User`를 공유하려면 역할도 한 곳에 있어야 한다.

## 모듈에서 쓰는 법

`settings.gradle`
```gradle
includeBuild('../../../../shared')
```
`build.gradle`
```gradle
implementation 'com.pixelplatform:shared'
```

Spring·QueryDSL·JWT·springdoc 의존성은 shared가 `api`로 노출하므로 모듈에서 다시
선언하지 않는다.

## 반드시 필요한 설정

공통 코어가 다른 패키지에 있으므로 **스캔 범위를 명시**해야 한다. 빠뜨리면 컴파일은
되지만 부팅 시 빈·엔티티·리포지토리를 못 찾는다.

```java
@SpringBootApplication(scanBasePackages = {"com.pixelfleet", "com.pixelplatform.core"})
@EntityScan(basePackages = {"com.pixelfleet", "com.pixelplatform.core"})
@EnableJpaRepositories(basePackages = {"com.pixelfleet", "com.pixelplatform.core"})
```

## 배포 시 주의

모듈 이미지의 **빌드 컨텍스트가 레포 루트**여야 한다(Docker는 컨텍스트 밖 `../`를 읽지
못한다). 이미지 안에서도 레포와 같은 디렉터리 배치를 유지해야 `includeBuild`의 상대
경로가 성립한다. 자세한 내용은 [docs/deploy-railway.md](../docs/deploy-railway.md).
