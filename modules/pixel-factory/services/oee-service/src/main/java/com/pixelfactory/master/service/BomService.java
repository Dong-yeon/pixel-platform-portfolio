package com.pixelfactory.master.service;

import com.pixelfactory.master.domain.Bom;
import com.pixelfactory.master.domain.Part;
import com.pixelfactory.master.dto.BomNodeResponse;
import com.pixelfactory.master.repository.BomRepository;
import com.pixelfactory.master.repository.PartRepository;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BOM 조회와 개정.
 *
 * <p><b>개정은 실 운영 MES에서 사고가 났던 자리다.</b> 대상 rev를 화면이 준 값 +1로 계산하면,
 * 최신이 아닌 rev를 보던 화면에서 개정할 때 이미 있는 rev와 부딪혀 같은 rev에 트리가 통째로
 * 중복 적재된다. 그래서 여기서는 <b>DB의 MAX(rev)+1</b>로만 대상을 정하고, 넣기 직전에
 * <b>같은 rev를 선삭제</b>해 같은 요청이 두 번 와도 행이 불어나지 않게 한다.
 */
@Service
@Transactional(readOnly = true)
public class BomService {

    private static final Logger log = LoggerFactory.getLogger(BomService.class);
    private static final String LATEST = "Y";
    /** 자기 참조가 섞여 들어와도 무한히 파고들지 않게 하는 한계. 실제 BOM은 5~6단이면 깊은 편이다. */
    private static final int MAX_DEPTH = 10;

    private final BomRepository bomRepository;
    private final PartRepository partRepository;
    private final PartService partService;

    public BomService(BomRepository bomRepository, PartRepository partRepository, PartService partService) {
        this.bomRepository = bomRepository;
        this.partRepository = partRepository;
        this.partService = partService;
    }

    /**
     * 품번의 최신 BOM 트리.
     *
     * <p>트리 조립을 서버가 한다 — 화면이 평면 목록을 문자열 키로 이어 붙이면 편집 중 상태가
     * 꼬인다(실 운영 MES에서 겪었다).
     */
    public BomNodeResponse getTree(String partCode) {
        Part root = partService.requirePart(partCode);
        Map<Long, Part> partsById = partRepository.findAll().stream()
                .collect(Collectors.toMap(Part::getId, Function.identity()));

        return buildNode(root, null, 0, partsById, new HashSet<>());
    }

    private BomNodeResponse buildNode(Part part, BigDecimal qtyPer, int level,
                                      Map<Long, Part> partsById, Set<Long> ancestors) {
        List<BomNodeResponse> children = new ArrayList<>();

        // 순환(A가 B를 품고 B가 A를 품는)이 들어오면 여기서 멈춘다. 마스터가 잘못돼도 서버가
        // 무한 재귀로 죽지는 않아야 한다.
        if (level < MAX_DEPTH && ancestors.add(part.getId())) {
            for (Bom line : bomRepository.findByParentPartIdAndLatestYnOrderBySeqAsc(part.getId(), LATEST)) {
                Part child = partsById.get(line.getChildPartId());
                if (child == null) {
                    log.warn("BOM에 없는 품번이 걸려 있다: parent={} childId={}", part.getPartCode(), line.getChildPartId());
                    continue;
                }
                children.add(buildNode(child, line.getQtyPer(), level + 1, partsById, ancestors));
            }
            ancestors.remove(part.getId());
        }

        return new BomNodeResponse(part.getPartCode(), part.getName(), part.getPartType(),
                part.getUnit(), qtyPer, level, children);
    }

    /**
     * 개정 — 최신 구성을 그대로 복사해 다음 rev를 만든다.
     *
     * <p>대상 rev는 <b>DB의 MAX+1</b>이다(화면이 보낸 rev를 믿지 않는다). 넣기 전에 그 rev를
     * 선삭제해 두 번 눌러도 결과가 같게 한다.
     *
     * @return 새로 만들어진 rev 번호
     */
    @Transactional
    public int copyRevision(String partCode) {
        Part parent = partService.requirePart(partCode);

        List<Bom> latest = bomRepository.findByParentPartIdAndLatestYnOrderBySeqAsc(parent.getId(), LATEST);
        if (latest.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "구성이 없는 품번은 개정할 수 없습니다: " + partCode);
        }

        Integer maxRev = bomRepository.findMaxRevNo(parent.getId());
        int nextRev = (maxRev == null ? 0 : maxRev) + 1;

        // 멱등 가드 — 같은 요청이 두 번 와도 행이 불어나지 않는다.
        bomRepository.deleteByParentPartIdAndRevNo(parent.getId(), nextRev);

        List<Bom> copies = latest.stream()
                .map(line -> new Bom(parent.getId(), line.getChildPartId(), nextRev,
                        line.getSeq(), line.getQtyPer()))
                .toList();

        // 이전 최신은 더 이상 최신이 아니다. 행은 남긴다 — 그 rev로 만든 물건을 되짚어야 한다.
        latest.forEach(Bom::supersede);
        bomRepository.saveAll(copies);

        log.info("BOM 개정: {} rev {} → {}", partCode, maxRev, nextRev);
        return nextRev;
    }

    /** 개정 이력 — rev별 줄 수. 화면이 "몇 차 개정까지 있는지" 보여준다. */
    public List<RevisionSummary> getRevisions(String partCode) {
        Part parent = partService.requirePart(partCode);
        Map<Integer, List<Bom>> byRev = bomRepository.findByParentPartIdOrderByRevNoDescSeqAsc(parent.getId())
                .stream()
                .collect(Collectors.groupingBy(Bom::getRevNo));

        return byRev.entrySet().stream()
                .map(entry -> new RevisionSummary(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().anyMatch(Bom::isLatest)))
                .sorted((a, b) -> Integer.compare(b.revNo(), a.revNo()))
                .toList();
    }

    public record RevisionSummary(int revNo, int lineCount, boolean latest) {}
}
