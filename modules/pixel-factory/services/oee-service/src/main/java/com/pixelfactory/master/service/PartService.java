package com.pixelfactory.master.service;

import com.pixelfactory.master.domain.Part;
import com.pixelfactory.master.domain.VehicleModel;
import com.pixelfactory.master.dto.PartResponse;
import com.pixelfactory.master.dto.VehicleModelResponse;
import com.pixelfactory.master.repository.PartRepository;
import com.pixelfactory.master.repository.VehicleModelRepository;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PartService {

    private final PartRepository partRepository;
    private final VehicleModelRepository modelRepository;

    public PartService(PartRepository partRepository, VehicleModelRepository modelRepository) {
        this.partRepository = partRepository;
        this.modelRepository = modelRepository;
    }

    public List<VehicleModelResponse> getModels() {
        return modelRepository.findAllByOrderByModelCodeAsc().stream()
                .map(VehicleModelResponse::from)
                .toList();
    }

    /** @param modelCode null이면 전체. 지정하면 그 차종 전용 품번만(공용 부품은 빠진다). */
    public List<PartResponse> getParts(String modelCode) {
        Map<Long, String> modelCodeById = modelCodeById();

        List<Part> parts;
        if (modelCode == null || modelCode.isBlank()) {
            parts = partRepository.findAllByOrderByPartCodeAsc();
        } else {
            VehicleModel model = modelRepository.findByModelCode(modelCode)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "차종을 찾을 수 없습니다: " + modelCode));
            parts = partRepository.findByModelIdOrderByPartCodeAsc(model.getId());
        }

        return parts.stream()
                .map(part -> PartResponse.from(part, modelCodeById.get(part.getModelId())))
                .toList();
    }

    public Part requirePart(String partCode) {
        return partRepository.findByPartCode(partCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "품번을 찾을 수 없습니다: " + partCode));
    }

    private Map<Long, String> modelCodeById() {
        return modelRepository.findAll().stream()
                .collect(Collectors.toMap(VehicleModel::getId, VehicleModel::getModelCode));
    }

    /** 작업지시 등 다른 도메인이 품번을 이름으로 붙일 때 쓴다. */
    public Map<Long, Part> partsById() {
        return partRepository.findAll().stream()
                .collect(Collectors.toMap(Part::getId, Function.identity()));
    }

    public Map<Long, String> modelCodesById() {
        return modelCodeById();
    }
}
