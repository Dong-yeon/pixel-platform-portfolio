package com.pixelqms.notification.repository;

import com.pixelqms.notification.domain.Notification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByOrderBySentAtDesc(Pageable pageable);
}
