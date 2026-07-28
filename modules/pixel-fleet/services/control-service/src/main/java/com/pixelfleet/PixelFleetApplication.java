package com.pixelfleet;

import com.pixelfleet.mqtt.MqttProperties;
import com.pixelplatform.core.auth.jwt.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 공통 코어(common/auth/user)는 {@code com.pixelplatform.core}에 있다. 기본 스캔은
 * 이 클래스의 패키지(com.pixelfleet)만 훑으므로, 컴포넌트·엔티티·리포지토리 스캔 범위를
 * 두 패키지로 명시해야 한다.
 */
@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.pixelfleet", "com.pixelplatform.core"})
@EntityScan(basePackages = {"com.pixelfleet", "com.pixelplatform.core"})
@EnableJpaRepositories(basePackages = {"com.pixelfleet", "com.pixelplatform.core"})
@EnableConfigurationProperties({JwtProperties.class, MqttProperties.class})
public class PixelFleetApplication {

    public static void main(String[] args) {
        SpringApplication.run(PixelFleetApplication.class, args);
    }
}
