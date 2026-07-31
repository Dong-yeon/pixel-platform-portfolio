package com.pixelfactory.master.repository;

import com.pixelfactory.master.domain.Bom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BomRepository extends JpaRepository<Bom, Long> {

    List<Bom> findByParentPartIdAndRevNoOrderBySeqAsc(Long parentPartId, Integer revNo);

    /**
     * 최신 구성만. 트리 조회가 쓰는 경로다.
     *
     * <p><b>latest_yn 조건이 빠지면 구버전 줄까지 섞여</b> 같은 자재가 여러 번 나온다 —
     * 개정이력 방식에서 제일 흔한 실수다.
     */
    List<Bom> findByParentPartIdAndLatestYnOrderBySeqAsc(Long parentPartId, String latestYn);

    List<Bom> findByParentPartIdOrderByRevNoDescSeqAsc(Long parentPartId);

    /**
     * 이 품번의 최신 rev 번호.
     *
     * <p><b>개정 대상 rev는 반드시 이 값 +1로 뽑는다.</b> 클라이언트가 보낸 rev에 +1을 하면,
     * 최신이 아닌 rev를 보고 있던 화면에서 개정할 때 이미 있는 rev와 부딪혀 <b>같은 rev에 트리가
     * 통째로 중복 적재된다</b>(실 운영 MES 사고). 대상은 화면이 아니라 DB가 정한다.
     *
     * @return 행이 없으면 null
     */
    @Query("select max(b.revNo) from Bom b where b.parentPartId = :parentPartId")
    Integer findMaxRevNo(@Param("parentPartId") Long parentPartId);

    /** 개정 전 선삭제 가드 — 같은 요청이 두 번 와도 행이 불어나지 않게(멱등). */
    void deleteByParentPartIdAndRevNo(Long parentPartId, Integer revNo);

    /** 여러 품번의 구성을 한 번에 — 트리를 조립할 때 N+1을 피한다. */
    List<Bom> findByParentPartIdInAndLatestYnOrderBySeqAsc(List<Long> parentPartIds, String latestYn);
}
