package com.pixelfactory.terminal.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * POP(Point of Production) 단말 — 현장 작업자가 착수·실적을 입력하는 키오스크.
 *
 * <p>설비·하역 지점과 같은 바닥(layout) 위에 서므로 좌표를 factory가 소유한다(V8 마이그레이션 주석 참고).
 * "설비 여러 대당 단말 1대"가 현실적이라 라인당 하나씩 둔다(POP-A1 / POP-B1).
 *
 * <p>작업자 위치는 <b>저장하지 않는다</b> — 가장 최근 TERMINAL 소스 이벤트에서 파생한다
 * (presence). 이 엔티티는 단말 마스터(코드·이름·좌표·라인)만 갖는다.
 *
 * <p>{@code pos_x}/{@code pos_y} 컬럼명을 명시하는 이유는 {@code LayoutNode} 주석 참고
 * (Hibernate 기본 네이밍이 {@code posX} → {@code posx}로 만든다).
 */
@Getter
@Entity
@Table(name = "pop_terminals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PopTerminal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String terminalCode;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private Long lineId;

    @Column(name = "pos_x", nullable = false)
    private Double posX;

    @Column(name = "pos_y", nullable = false)
    private Double posY;

    public PopTerminal(String terminalCode, String name, Long lineId, double posX, double posY) {
        this.terminalCode = terminalCode;
        this.name = name;
        this.lineId = lineId;
        this.posX = posX;
        this.posY = posY;
    }
}
