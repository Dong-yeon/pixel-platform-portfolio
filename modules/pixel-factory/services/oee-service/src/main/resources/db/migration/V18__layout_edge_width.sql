-- P25: 통로폭을 라우팅에 강제하기 위한 첫 번째 조각 — 엣지에 물리적 폭을 싣는다.
--
-- **왜.** AMR 사양서(§4.3)는 공차(空車) 단일 통로 ≥950mm, 적재 시 단일 통로 ≥1400mm를
-- 요구한다. fleet의 LaneGraph가 이 폭을 실제로 강제하려면(설계 근거:
-- docs/p25-robot-spec-routing-design.md) 엣지가 자기 폭을 알아야 한다. 폭은 통로의 물리적
-- 사실이라 factory 소관이다(로봇 개념이 스며들면 안 된다는 원칙은 그대로 지킨다 — 여기엔
-- "로봇"도 "적재"도 없고 순수하게 "이 통로가 몇 mm인가"만 있다).
--
-- **왜 전부 2000mm인가.** 실측 통로폭 데이터가 없다. 950mm/1400mm 두 임계값을 넉넉히
-- 웃도는 값으로 시드해 지금 데모 레이아웃의 모든 통로가 "AMR이 다니기 충분히 넓다"는
-- 방어 가능한 가정을 세운다. 의도적으로 좁은 구간을 인위로 만들지 않는다 — 그러면 기존
-- 데모 트래픽이 예기치 않게 막히고, 그건 "라우팅 검증"이 아니라 "회귀 유발"이다. 강제
-- 로직 자체의 정확성은 fleet 쪽 전용 단위 테스트(LaneGraphTest)로 증명한다(design doc 5절).
alter table layout_edges add column width_mm integer;
update layout_edges set width_mm = 2000;
alter table layout_edges alter column width_mm set not null;
