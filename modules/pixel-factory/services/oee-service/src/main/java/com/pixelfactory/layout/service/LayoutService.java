package com.pixelfactory.layout.service;

import com.pixelfactory.layout.domain.LayoutSettings;
import com.pixelfactory.layout.dto.LayoutResponse;
import com.pixelfactory.layout.repository.LayoutNodeRepository;
import com.pixelfactory.layout.repository.LayoutSettingsRepository;
import com.pixelfactory.terminal.repository.PopTerminalRepository;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LayoutService {

    private final LayoutSettingsRepository settingsRepository;
    private final LayoutNodeRepository nodeRepository;
    private final PopTerminalRepository terminalRepository;

    public LayoutService(LayoutSettingsRepository settingsRepository, LayoutNodeRepository nodeRepository,
                         PopTerminalRepository terminalRepository) {
        this.settingsRepository = settingsRepository;
        this.nodeRepository = nodeRepository;
        this.terminalRepository = terminalRepository;
    }

    public LayoutResponse get() {
        LayoutSettings settings = settingsRepository.findById(LayoutSettings.SINGLETON_ID)
                // 시드가 없으면 지도를 그릴 수 없다. 0×0 같은 값을 만들어 내보내면 화면이
                // 조용히 비어 원인을 찾기 어려우므로 여기서 끊는다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "평면도 설정이 없습니다. V7 마이그레이션이 적용됐는지 확인하세요."));

        return LayoutResponse.of(settings,
                nodeRepository.findAllByOrderByNodeCodeAsc(),
                terminalRepository.findAllByOrderByTerminalCodeAsc());
    }
}
