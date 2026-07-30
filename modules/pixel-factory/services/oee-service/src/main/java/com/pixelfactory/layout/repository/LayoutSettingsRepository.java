package com.pixelfactory.layout.repository;

import com.pixelfactory.layout.domain.LayoutSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayoutSettingsRepository extends JpaRepository<LayoutSettings, Short> {
}
