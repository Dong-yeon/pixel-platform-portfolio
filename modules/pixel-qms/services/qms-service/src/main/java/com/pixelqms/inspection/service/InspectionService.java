package com.pixelqms.inspection.service;

import com.pixelqms.factory.FactoryQualityClient;
import com.pixelqms.inspection.domain.Inspection;
import com.pixelqms.inspection.domain.InspectionResult;
import com.pixelqms.inspection.domain.InspectionType;
import com.pixelqms.inspection.dto.InspectionCompleteRequest;
import com.pixelqms.inspection.dto.InspectionResponse;
import com.pixelqms.inspection.repository.DefectTypeRepository;
import com.pixelqms.inspection.repository.InspectionRepository;
import com.pixelqms.ncr.domain.Nonconformance;
import com.pixelqms.ncr.repository.NonconformanceRepository;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검사 — 품질 프로세스의 입구.
 *
 * <p>factory가 불량 임계를 넘겼다고 알리면 공정검사가 자동 생성되고, 검사원이 판정한다.
 * 불합격이면 부적합(NCR)이 함께 만들어져 MRB 심의로 올라갈 수 있다.
 */
@Service
@Transactional(readOnly = true)
public class InspectionService {

    private static final Logger log = LoggerFactory.getLogger(InspectionService.class);

    private final InspectionRepository inspectionRepository;
    private final NonconformanceRepository nonconformanceRepository;
    private final DefectTypeRepository defectTypeRepository;
    private final FactoryQualityClient factoryQualityClient;

    public InspectionService(
            InspectionRepository inspectionRepository,
            NonconformanceRepository nonconformanceRepository,
            DefectTypeRepository defectTypeRepository,
            FactoryQualityClient factoryQualityClient
    ) {
        this.inspectionRepository = inspectionRepository;
        this.nonconformanceRepository = nonconformanceRepository;
        this.defectTypeRepository = defectTypeRepository;
        this.factoryQualityClient = factoryQualityClient;
    }

    /**
     * factory 신호로 공정검사를 만든다.
     *
     * <p><b>같은 작업지시의 검사는 하나만.</b> factory가 재기동하면 신호를 한 번 더 보낼 수
     * 있으므로 수신 측이 멱등이어야 한다(MQTT도 최소 1회 전달이다).
     */
    @Transactional
    public void createFromFactorySignal(String equipmentCode, String workOrderNo, String lotNo, int defectQty) {
        String inspectionNo = "INS-" + workOrderNo;
        if (inspectionRepository.existsByInspectionNo(inspectionNo)) {
            log.debug("이미 생성된 검사입니다: {}", inspectionNo);
            return;
        }

        Inspection inspection = inspectionRepository.save(new Inspection(
                inspectionNo, InspectionType.IN_PROCESS, equipmentCode, workOrderNo, lotNo, 0, defectQty));

        // factory 타임라인에도 검사가 시작됐음을 남긴다(INSPECTION_STARTED).
        factoryQualityClient.inspectionStarted(equipmentCode, workOrderNo, lotNo, inspectionNo);
        log.info("검사 생성(불량 임계 초과): {} — 설비 {} / 불량 {}개", inspectionNo, equipmentCode, defectQty);
    }

    public List<InspectionResponse> getInspections() {
        return inspectionRepository.findByOrderByIdDesc().stream().map(InspectionResponse::from).toList();
    }

    /** 검사 대기 목록 — INSPECTOR의 진입 화면. */
    public List<InspectionResponse> getPending() {
        return inspectionRepository.findByResultOrderByIdDesc(InspectionResult.PENDING)
                .stream().map(InspectionResponse::from).toList();
    }

    /**
     * 검사 판정. 불합격이면 부적합(NCR)을 함께 만든다.
     *
     * @return 판정된 검사
     */
    @Transactional
    public InspectionResponse complete(Long id, InspectionCompleteRequest request, Long inspectorId) {
        Inspection inspection = inspectionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "검사를 찾을 수 없습니다. id=" + id));
        if (!inspection.isPending()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 판정된 검사입니다: " + inspection.getInspectionNo());
        }
        if (request.result() == InspectionResult.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "판정 결과는 PASSED 또는 FAILED여야 합니다.");
        }

        inspection.complete(request.result(), inspectorId, request.inspectedQty(),
                request.defectQty(), request.note());

        if (request.result() == InspectionResult.FAILED) {
            createNonconformance(inspection, request.defectCode());
        }

        factoryQualityClient.inspectionResult(
                inspection.getEquipmentCode(), inspection.getWorkOrderNo(), inspection.getLotNo(),
                inspection.getInspectionNo(), request.result() == InspectionResult.PASSED);

        return InspectionResponse.from(inspection);
    }

    private void createNonconformance(Inspection inspection, String defectCode) {
        Long defectTypeId = defectCode == null ? null
                : defectTypeRepository.findByDefectCode(defectCode).map(d -> d.getId()).orElse(null);

        String ncrNo = "NCR-" + inspection.getInspectionNo().replace("INS-", "");
        if (nonconformanceRepository.existsByNcrNo(ncrNo)) {
            return;
        }

        nonconformanceRepository.save(new Nonconformance(
                ncrNo,
                inspection.getId(),
                defectTypeId,
                inspection.getEquipmentCode(),
                inspection.getWorkOrderNo(),
                inspection.getLotNo(),
                inspection.getDefectQty(),
                "검사 불합격: " + inspection.getInspectionNo()
        ));
        log.info("부적합 생성: {}", ncrNo);
    }
}
