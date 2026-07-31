package com.pixelfactory.layout.domain;

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

/** 건물의 층. 창고동만 3개이고 나머지는 1개다. 층 선택 드롭다운의 항목이 된다. */
@Getter
@Entity
@Table(name = "layout_floors")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LayoutFloor extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long buildingId;

    @Column(nullable = false)
    private Short floorNo;

    @Column(nullable = false, length = 50)
    private String name;
}
