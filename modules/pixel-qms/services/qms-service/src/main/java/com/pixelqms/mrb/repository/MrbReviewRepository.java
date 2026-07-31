package com.pixelqms.mrb.repository;

import com.pixelqms.mrb.domain.MrbReview;
import com.pixelqms.mrb.domain.MrbStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MrbReviewRepository extends JpaRepository<MrbReview, Long> {

    boolean existsByMrbNo(String mrbNo);

    List<MrbReview> findByOrderByIdDesc();

    /** 지도의 "품질관리실" 대기 건수 배지에 쓴다. */
    long countByStatusIn(List<MrbStatus> statuses);

    List<MrbReview> findByStatusInOrderByIdDesc(List<MrbStatus> statuses);
}
