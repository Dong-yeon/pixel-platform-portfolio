package com.pixelfactory.equipment.domain;

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

@Getter
@Entity
@Table(name = "production_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String lineCode;

    @Column(nullable = false, length = 50)
    private String name;

    public ProductionLine(String lineCode, String name) {
        this.lineCode = lineCode;
        this.name = name;
    }
}
