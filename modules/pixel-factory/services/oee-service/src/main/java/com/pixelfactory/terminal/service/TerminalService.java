package com.pixelfactory.terminal.service;

import com.pixelfactory.event.domain.FactoryEvent;
import com.pixelfactory.event.domain.SourceType;
import com.pixelfactory.event.repository.FactoryEventRepository;
import com.pixelfactory.terminal.PopProperties;
import com.pixelfactory.terminal.domain.PopTerminal;
import com.pixelfactory.terminal.dto.PopBoardResponse;
import com.pixelfactory.terminal.dto.TerminalPresenceResponse;
import com.pixelfactory.terminal.dto.TerminalResponse;
import com.pixelfactory.terminal.repository.PopTerminalRepository;
import com.pixelfactory.workorder.domain.WorkOrder;
import com.pixelfactory.workorder.domain.WorkOrderStatus;
import com.pixelfactory.workorder.repository.WorkOrderRepository;
import com.pixelfactory.workorder.service.WorkOrderService;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import com.pixelplatform.core.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TerminalService {

    private final PopTerminalRepository terminalRepository;
    private final FactoryEventRepository factoryEventRepository;
    private final WorkOrderRepository workOrderRepository;
    private final UserRepository userRepository;
    private final WorkOrderService workOrderService;
    private final PopProperties popProperties;

    public TerminalService(
            PopTerminalRepository terminalRepository,
            FactoryEventRepository factoryEventRepository,
            WorkOrderRepository workOrderRepository,
            UserRepository userRepository,
            WorkOrderService workOrderService,
            PopProperties popProperties
    ) {
        this.terminalRepository = terminalRepository;
        this.factoryEventRepository = factoryEventRepository;
        this.workOrderRepository = workOrderRepository;
        this.userRepository = userRepository;
        this.workOrderService = workOrderService;
        this.popProperties = popProperties;
    }

    public List<TerminalResponse> getTerminals() {
        return terminalRepository.findAllByOrderByTerminalCodeAsc()
                .stream()
                .map(TerminalResponse::from)
                .toList();
    }

    /** POP 화면 초기 데이터 — 단말 + 현재 작업자에게 배정된 작업지시. */
    public PopBoardResponse getBoard(String terminalCode, Long userId) {
        PopTerminal terminal = requireTerminal(terminalCode);
        return new PopBoardResponse(
                TerminalResponse.from(terminal),
                workOrderService.getMyWorkOrders(userId)
        );
    }

    public Long requireTerminalId(String terminalCode) {
        return requireTerminal(terminalCode).getId();
    }

    private PopTerminal requireTerminal(String terminalCode) {
        return terminalRepository.findByTerminalCode(terminalCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "단말을 찾을 수 없습니다: " + terminalCode));
    }

    /**
     * 파생 위치(presence). 저장하지 않고 최근 TERMINAL 소스 이벤트에서 계산한다.
     *
     * <p>타임아웃 이내의 TERMINAL 이벤트를 최신순으로 받아 단말별 첫 1건만 채택하고,
     * 그 작업지시가 이미 종료(COMPLETED/CANCELLED)됐으면 배지를 뗀다.
     */
    public List<TerminalPresenceResponse> getPresence() {
        LocalDateTime since = LocalDateTime.now().minusMinutes(popProperties.getPresenceTimeoutMinutes());
        List<FactoryEvent> events = factoryEventRepository
                .findBySourceTypeAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(SourceType.TERMINAL, since);

        Set<Long> seenTerminals = new HashSet<>();
        List<TerminalPresenceResponse> presence = new ArrayList<>();

        for (FactoryEvent event : events) {
            Long terminalId = event.getSourceId();
            Long workOrderId = event.getWorkOrderId();
            if (terminalId == null || workOrderId == null || !seenTerminals.add(terminalId)) {
                continue; // 단말별 최신 1건만 — 이미 본 단말/불완전한 이벤트는 건너뛴다.
            }

            WorkOrder workOrder = workOrderRepository.findById(workOrderId).orElse(null);
            if (workOrder == null || isClosed(workOrder.getStatus())) {
                continue; // 종료된 작업지시는 배지를 뗀다(스펙).
            }

            PopTerminal terminal = terminalRepository.findById(terminalId).orElse(null);
            if (terminal == null) {
                continue;
            }

            String operatorName = userRepository.findById(workOrder.getAssignedUserId())
                    .map(user -> user.getName())
                    .orElse("작업자");

            presence.add(new TerminalPresenceResponse(
                    terminal.getTerminalCode(),
                    operatorName,
                    workOrder.getWorkOrderNo(),
                    event.getOccurredAt()
            ));
        }

        return presence;
    }

    private boolean isClosed(WorkOrderStatus status) {
        return status == WorkOrderStatus.COMPLETED || status == WorkOrderStatus.CANCELLED;
    }
}
