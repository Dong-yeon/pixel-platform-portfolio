package com.pixelwms.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelwms.item.domain.Item;
import com.pixelwms.item.repository.ItemRepository;
import com.pixelwms.stock.domain.Location;
import com.pixelwms.stock.domain.MovementType;
import com.pixelwms.stock.domain.Pallet;
import com.pixelwms.stock.domain.PalletStatus;
import com.pixelwms.stock.domain.Stock;
import com.pixelwms.stock.domain.StockMovement;
import com.pixelwms.stock.repository.LocationRepository;
import com.pixelwms.stock.repository.PalletRepository;
import com.pixelwms.stock.repository.StockMovementRepository;
import com.pixelwms.stock.repository.StockRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * StockService의 실제 재고 불변식 회귀 테스트 (P23 — 파렛트 단위).
 *
 * <p>{@link Pallet}/{@link Item}/{@link Location}은 JPA {@code @GeneratedValue} id라 생성자로
 * 값을 못 준다 — 리포지토리는 전부 mock하고, id가 꼭 필요한 시나리오(FIFO 비교, 재출고 방지)만
 * {@link ReflectionTestUtils}로 테스트 픽스처에 id를 심는다. 그 외 저장 경로는 mock이
 * {@code save()}에 넘어온 인자를 그대로 돌려주게 해 실제 서비스 로직(검증→저장→이력 기록
 * 순서)만 태운다.
 */
class StockServiceRegressionTest {

    private StockRepository stockRepository;
    private PalletRepository palletRepository;
    private StockMovementRepository movementRepository;
    private LocationRepository locationRepository;
    private ItemRepository itemRepository;
    private StockService stockService;

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        palletRepository = mock(PalletRepository.class);
        PalletCodeGenerator palletCodeGenerator = mock(PalletCodeGenerator.class);
        movementRepository = mock(StockMovementRepository.class);
        locationRepository = mock(LocationRepository.class);
        itemRepository = mock(ItemRepository.class);

        when(palletCodeGenerator.next()).thenReturn("PLT-0001");
        when(palletRepository.save(any(Pallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockRepository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));

        stockService = new StockService(
                stockRepository, palletRepository, palletCodeGenerator,
                movementRepository, locationRepository, itemRepository);
    }

    private static Location noSlotLimitLocation() {
        Location location = new Location("LOC-A", "A동", "NODE-A");
        ReflectionTestUtils.setField(location, "id", 1L);
        return location;
    }

    /**
     * 사양서 "총중량 500kg 미만" 상한(D4)의 경계값 테스트. 단위중량 100kg × 5개 = 정확히
     * 500kg인 파렛트는 "미만"을 만족하지 못하므로 거절돼야 한다 — {@code compareTo(...) >= 0}
     * 조건이 실수로 {@code > 0}으로 바뀌면(경계 포함 누락) 이 테스트가 잡아낸다.
     */
    @Test
    void 파렛트_총중량이_500kg에_도달하면_입고를_거절한다() {
        Item item = new Item("ITEM-1", "부품A", "EA");
        ReflectionTestUtils.setField(item, "unitWeightKg", BigDecimal.valueOf(100));
        Location location = noSlotLimitLocation();

        assertThatThrownBy(() -> stockService.receive(location, item, 5, "IN-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("500");

        verify(palletRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    /**
     * 단위중량이 없는 품목(레거시/미등록 데이터)은 총중량 검증을 건너뛰고 정상 입고돼야 한다
     * (D4 — "없는 데이터로 억지로 막지 않는다"). 수량이 아무리 커도 막히면 안 된다.
     */
    @Test
    void 단위중량이_없는_품목은_총중량_검증_없이_입고된다() {
        Item item = new Item("ITEM-2", "단위중량_미등록품", "EA");
        Location location = noSlotLimitLocation();
        when(palletRepository.countByLocationIdAndStatusNot(1L, PalletStatus.RETIRED)).thenReturn(0L);

        Pallet pallet = stockService.receive(location, item, 999_999, "IN-2");

        assertThat(pallet.getWeightKg()).isNull();
        assertThat(pallet.getStatus()).isEqualTo(PalletStatus.LOADED);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getMovementType()).isEqualTo(MovementType.INBOUND);
        assertThat(movementCaptor.getValue().getQuantityDelta()).isEqualTo(999_999);
    }

    /**
     * 로케이션의 파렛트 슬롯(D7)이 이미 꽉 찼으면(RETIRED가 아닌 파렛트 수 >= maxPallet)
     * 무게와 무관하게 입고 자체를 거절해야 한다 — 슬롯 없는 곳에 파렛트를 올릴 수 없다.
     */
    @Test
    void 로케이션_파렛트_슬롯이_가득_차면_입고를_거절한다() {
        Item item = new Item("ITEM-3", "부품C", "EA");
        Location location = new Location("LOC-B", "B동", "NODE-B");
        ReflectionTestUtils.setField(location, "id", 2L);
        ReflectionTestUtils.setField(location, "maxPallet", 2);
        when(palletRepository.countByLocationIdAndStatusNot(2L, PalletStatus.RETIRED)).thenReturn(2L);

        assertThatThrownBy(() -> stockService.receive(location, item, 1, "IN-3"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("슬롯이 가득");

        verify(palletRepository, never()).save(any());
    }

    /**
     * 출고 대상 파렛트 자동 선택(FIFO, D3) — 같은 로케이션·품목이라도 나중에 입고된 파렛트가
     * 먼저 리포지토리 목록에 나와도, {@code inboundDt}가 더 빠른 파렛트를 골라야 한다.
     * 입출고 순서가 재고 회전율에 직결되는 실제 규칙이라 리스트 순서에 의존하면 안 된다.
     */
    @Test
    void findFifoPallet은_리포지토리_반환_순서와_무관하게_입고가_가장_빠른_파렛트를_고른다() {
        Pallet older = new Pallet("PLT-OLD", 10L, null);
        ReflectionTestUtils.setField(older, "id", 100L);
        Pallet newer = new Pallet("PLT-NEW", 10L, null);
        ReflectionTestUtils.setField(newer, "id", 101L);

        // 일부러 "나중에 입고된 파렛트"를 리포지토리 목록 맨 앞에 둔다 — 정렬을 리스트 순서에
        // 기대는 게 아니라 실제 inboundDt 비교로 하는지 검증한다.
        when(palletRepository.findByLocationIdAndStatus(10L, PalletStatus.LOADED))
                .thenReturn(List.of(newer, older));
        when(palletRepository.findAllById(List.of(101L, 100L)))
                .thenReturn(List.of(newer, older));

        Stock stockOnNewer = new Stock(101L, 999L, 5, "PLT-NEW", LocalDateTime.of(2026, 1, 5, 0, 0));
        Stock stockOnOlder = new Stock(100L, 999L, 5, "PLT-OLD", LocalDateTime.of(2026, 1, 1, 0, 0));
        when(stockRepository.findByPalletIdInAndItemId(List.of(101L, 100L), 999L))
                .thenReturn(List.of(stockOnNewer, stockOnOlder));

        Optional<Pallet> picked = stockService.findFifoPallet(10L, 999L);

        assertThat(picked).isPresent();
        assertThat(picked.get().getPltCode()).isEqualTo("PLT-OLD");
    }

    /**
     * 운송 완료 처리(issuePallet)의 핵심 불변식 두 가지를 한 시나리오로 검증한다.
     * <ol>
     *   <li>파렛트는 부분 소진 없이 통째로 나간다(D5) — 재고 행 삭제 + 이동이력에 원래 수량의
     *   음수 delta 기록 + 파렛트 RETIRED 전환.</li>
     *   <li>같은 파렛트로 다시 출고 처리를 시도하면(중복 완료 통지 등) 거절돼야 한다 — 재고
     *   행이 이미 삭제됐으므로 {@code requireStockOfPallet}이 예외를 던진다. 이 가드가
     *   없으면 이미 빈 파렛트에서 또 마이너스 이동이력이 쌓일 수 있다.</li>
     * </ol>
     */
    @Test
    void issuePallet은_파렛트를_통째로_소진하고_이미_소진된_파렛트는_다시_출고할_수_없다() {
        Pallet pallet = new Pallet("PLT-9", 20L, null);
        ReflectionTestUtils.setField(pallet, "id", 900L);
        Stock stock = new Stock(900L, 5L, 30, "PLT-9", LocalDateTime.now());

        when(palletRepository.findById(900L)).thenReturn(Optional.of(pallet));
        when(stockRepository.findByPalletId(900L))
                .thenReturn(Optional.of(stock))
                .thenReturn(Optional.empty()); // 첫 호출 후 재고 행이 삭제된 상태를 흉내낸다.

        stockService.issuePallet(900L, "OUT-1");

        assertThat(pallet.getStatus()).isEqualTo(PalletStatus.RETIRED);
        verify(stockRepository).delete(stock);
        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getMovementType()).isEqualTo(MovementType.OUTBOUND);
        assertThat(movementCaptor.getValue().getQuantityDelta()).isEqualTo(-30);

        assertThatThrownBy(() -> stockService.issuePallet(900L, "OUT-2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("재고가 없습니다");
    }
}
