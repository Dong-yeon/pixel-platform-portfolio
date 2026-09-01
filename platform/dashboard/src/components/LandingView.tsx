/**
 * 랜딩 페이지(P15) — 방문자가 로그인 화면부터 만나면 안 된다.
 *
 * <p>4개 시스템 소개 + 아키텍처 다이어그램 + 데모 계정 안내를 로그인보다 먼저
 * 보여준다. 내용은 루트 README.md("System Architecture"·"Key Engineering
 * Features"·데모 계정)와 항상 같은 사실을 가리켜야 한다 — 여기서 새 숫자·설명을
 * 지어내지 않는다.
 *
 * <p>인증 로직은 안 둔다(단일 진실 지점 유지) — "둘러보기" 버튼은 로그인 화면으로만
 * 넘기고, 실제 로그인(게스트 원클릭 포함)은 여전히 {@code LoginView}가 전담한다.
 */
export function LandingView({ onEnter }: { onEnter: () => void }) {
  return (
    <div className="landing">
      <header className="landing-hero">
        <h1>Pixel Platform</h1>
        <p className="landing-tagline">
          Spring Cloud Gateway + MQTT 기반 실시간 IoT/로봇 군집 관제(FMS) 마이크로서비스 플랫폼
        </p>
        <p className="landing-lede">
          제조 현장의 서로 다른 도메인(가공 설비 OEE, AMR 로봇 군집 관제, 창고 재고, 품질 검사)을
          API Gateway + 중앙 인증 아래 4개의 독립 서비스로 묶어, MQTT·Redis·WebSocket 기반
          실시간 데이터를 하나의 대시보드로 통합해서 보여줍니다.
        </p>
        <div className="landing-cta">
          <button type="button" onClick={onEnter}>지금 둘러보기 →</button>
          <a
            className="landing-ghost-link"
            href="https://github.com/Dong-yeon/pixel-platform-portfolio"
            target="_blank"
            rel="noreferrer"
          >
            GitHub 저장소
          </a>
        </div>
      </header>

      <section className="landing-section">
        <h2>4개 시스템</h2>
        <div className="landing-modules">
          <ModuleCard
            name="PixelFactory" role="OEE·설비 관제" port="9001" accent="#2d7ff9"
            desc="가공 라인 설비 텔레메트리를 MQTT로 수집해 OEE(가동률×성능×품질)를 실시간 계산합니다. MES 마스터데이터·품질 홀드·POP 단말까지 포함해 코드 규모가 가장 큰 모듈입니다."
          />
          <ModuleCard
            name="PixelFleet" role="AMR 군집 관제" port="9002" accent="#8e44ad"
            desc="가장 기술적으로 깊은 모듈. 노드-엣지 그래프 + A*/Dijkstra 경로탐색으로 다중 건물을 넘나들고, 구간(segment) 단위 점유 예약으로 여러 로봇의 교통정리·배터리 인지 배차를 직접 구현했습니다."
          />
          <ModuleCard
            name="PixelWMS" role="창고·재고" port="9003" accent="#0f9b8e"
            desc="출고지시 하나가 fleet에 실제 운송 작업을 만들고, 운송이 끝나면 재고가 자동으로 반영됩니다 — REST·MQTT 계약만으로 두 도메인이 이어집니다."
          />
          <ModuleCard
            name="PixelQMS" role="품질·부적합(MRB)" port="9004" accent="#e08a00"
            desc="불량이 임계를 넘으면 factory가 발행한 이벤트를 받아 검사를 만들고, MRB(자재검토위원회)가 열리면 별개 서비스·별개 DB인 설비를 QUALITY_HOLD로 전환합니다."
          />
        </div>
      </section>

      <section className="landing-section">
        <h2>아키텍처</h2>
        <ArchitectureDiagram />
      </section>

      <section className="landing-section">
        <h2>핵심 엔지니어링 포인트</h2>
        <ul className="landing-features">
          <li>동적 그래프 기반 경로계산 — 통로 좌표를 컴파일타임 상수로 고정한 규칙 라우터를 노드-엣지 그래프 + 경로탐색으로 교체, 장애물 발생 시 진행 중인 로봇이 실시간 재경로</li>
          <li>품질 홀드·MRB 심의·Outbox 알림 — 별개 서비스·별개 DB인 두 도메인이 REST 계약만으로 연동, 실제 SMTP 없이도 발송 이력을 화면에서 확인 가능한 확장점 설계</li>
          <li>API Gateway 중앙 인증 — 게이트웨이 레벨 JWT 검증 후 신원 헤더 주입, 클라이언트가 보낸 인증 헤더는 게이트웨이가 제거(스푸핑 방지)</li>
          <li>관측성 — 이벤트 적재 지연(p50/p95/p99)·미배차 대기 주문 수·레인 구간 점유 수를 Prometheus 커스텀 메트릭으로 상시 노출</li>
          <li>컴포저블 도메인 설계 — 모듈별 독립 DB, 모듈 간 직접 코드/DB 참조 금지. 아무 모듈이나 내려도 나머지는 정상 동작</li>
        </ul>
      </section>

      <section className="landing-section">
        <h2>데모 계정</h2>
        <p className="muted small">비밀번호는 전부 <span className="mono">password</span>입니다 — 둘러보기 화면에서 바로 로그인해 보세요.</p>
        <div className="landing-accounts">
          <AccountCard id="admin" role="관리자" desc="전체 화면 + 이벤트 주입 컨트롤 패널까지 접근" />
          <AccountCard id="dispatcher" role="배차 담당자" desc="PixelFleet 관제 화면" />
          <AccountCard id="operator" role="작업자" desc="POP 단말 화면" />
          <AccountCard id="inspector" role="검사 담당자" desc="품질(검사·MRB)·발송함 화면" />
        </div>
      </section>

      <footer className="landing-footer">
        <button type="button" onClick={onEnter}>지금 둘러보기 →</button>
      </footer>
    </div>
  )
}

function ModuleCard({
  name, role, port, accent, desc,
}: {
  name: string; role: string; port: string; accent: string; desc: string
}) {
  return (
    <div className="landing-module-card" style={{ borderTopColor: accent }}>
      <div className="landing-module-head">
        <h3>{name}</h3>
        <span className="mono small muted">:{port}</span>
      </div>
      <div className="landing-module-role" style={{ color: accent }}>{role}</div>
      <p className="small">{desc}</p>
    </div>
  )
}

function AccountCard({ id, role, desc }: { id: string; role: string; desc: string }) {
  return (
    <div className="landing-account-card">
      <span className="mono">{id}</span>
      <span className="landing-account-role">{role}</span>
      <span className="muted small">{desc}</span>
    </div>
  )
}

/**
 * README "System Architecture" 박스 다이어그램과 같은 내용을 CSS로 그린다 — ASCII
 * 아트를 그대로 옮기지 않고 박스·화살표로 다시 그리되, 가리키는 서비스·포트·역할은
 * README와 한 글자도 다르지 않게 맞춘다.
 */
function ArchitectureDiagram() {
  return (
    <div className="landing-arch">
      <div className="landing-arch-box landing-arch-top">
        통합 대시보드(React)
        <span className="muted small"> · Overview/Factory/Fleet/WMS/QMS 탭 · :9200</span>
      </div>
      <div className="landing-arch-arrow">HTTP / WebSocket(STOMP)</div>
      <div className="landing-arch-box landing-arch-top">
        API Gateway
        <span className="muted small"> · Spring Cloud Gateway 라우팅 + 중앙 JWT 인증 · :9000</span>
      </div>
      <div className="landing-arch-arrow">↓</div>
      <div className="landing-arch-row">
        <div className="landing-arch-box">PixelFactory<span className="muted small"><br />MQTT Event Sourcing · :9001</span></div>
        <div className="landing-arch-box">PixelFleet<span className="muted small"><br />MQTT + Redis + WS 교통정리 · :9002</span></div>
        <div className="landing-arch-box">PixelWMS<span className="muted small"><br />창고 마스터/재고 · :9003</span></div>
        <div className="landing-arch-box">PixelQMS<span className="muted small"><br />검사·홀드/릴리즈 · :9004</span></div>
      </div>
    </div>
  )
}
