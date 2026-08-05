package com.pixelfactory.layout.service;

import com.pixelfactory.layout.domain.LayoutFloor;
import com.pixelfactory.layout.domain.LayoutSettings;
import com.pixelfactory.layout.dto.LayoutResponse;
import com.pixelfactory.layout.repository.LayoutBuildingRepository;
import com.pixelfactory.layout.repository.LayoutChargingZoneRepository;
import com.pixelfactory.layout.repository.LayoutEdgeRepository;
import com.pixelfactory.layout.repository.LayoutElevatorRepository;
import com.pixelfactory.layout.repository.LayoutFloorRepository;
import com.pixelfactory.layout.repository.LayoutNodeRepository;
import com.pixelfactory.layout.repository.LayoutRackRepository;
import com.pixelfactory.layout.repository.LayoutSettingsRepository;
import com.pixelfactory.terminal.repository.PopTerminalRepository;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LayoutService {

    private final LayoutSettingsRepository settingsRepository;
    private final LayoutNodeRepository nodeRepository;
    private final PopTerminalRepository terminalRepository;
    private final LayoutBuildingRepository buildingRepository;
    private final LayoutFloorRepository floorRepository;
    private final LayoutRackRepository rackRepository;
    private final LayoutElevatorRepository elevatorRepository;
    private final LayoutChargingZoneRepository chargingZoneRepository;
    private final LayoutEdgeRepository edgeRepository;

    public LayoutService(LayoutSettingsRepository settingsRepository, LayoutNodeRepository nodeRepository,
                         PopTerminalRepository terminalRepository, LayoutBuildingRepository buildingRepository,
                         LayoutFloorRepository floorRepository, LayoutRackRepository rackRepository,
                         LayoutElevatorRepository elevatorRepository,
                         LayoutChargingZoneRepository chargingZoneRepository,
                         LayoutEdgeRepository edgeRepository) {
        this.settingsRepository = settingsRepository;
        this.nodeRepository = nodeRepository;
        this.terminalRepository = terminalRepository;
        this.buildingRepository = buildingRepository;
        this.floorRepository = floorRepository;
        this.rackRepository = rackRepository;
        this.elevatorRepository = elevatorRepository;
        this.chargingZoneRepository = chargingZoneRepository;
        this.edgeRepository = edgeRepository;
    }

    public LayoutResponse get() {
        LayoutSettings settings = settingsRepository.findById(LayoutSettings.SINGLETON_ID)
                // 시드가 없으면 지도를 그릴 수 없다. 0×0 같은 값을 만들어 내보내면 화면이
                // 조용히 비어 원인을 찾기 어려우므로 여기서 끊는다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "평면도 설정이 없습니다. 평면도 마이그레이션이 적용됐는지 확인하세요."));

        Map<Long, List<LayoutFloor>> floorsByBuilding = floorRepository.findAllByOrderByBuildingIdAscFloorNoAsc()
                .stream()
                .collect(Collectors.groupingBy(LayoutFloor::getBuildingId));

        return LayoutResponse.of(
                settings,
                buildingRepository.findAllByOrderByDisplayOrderAsc(),
                floorsByBuilding,
                nodeRepository.findAllByOrderByNodeCodeAsc(),
                terminalRepository.findAllByOrderByTerminalCodeAsc(),
                rackRepository.findAllByOrderByRackCodeAsc(),
                elevatorRepository.findAllByOrderByElevatorCodeAsc(),
                chargingZoneRepository.findAllByOrderByZoneCodeAsc(),
                edgeRepository.findAllByOrderByIdAsc());
    }
}
