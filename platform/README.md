# platform

플랫폼 공통 레이어. 도메인 모듈(`modules/`)과 달리 특정 산업 도메인에 속하지 않는다.

| 경로 | 역할 | 포트 | 상태 |
|---|---|---|---|
| `gateway/` | API Gateway — 단일 진입점, 라우팅·CORS 집약 | 9000 | 완료 |
| `dashboard/` | 통합 React 대시보드 (OEE + AMR + KPI) | 9200 | 완료 |
