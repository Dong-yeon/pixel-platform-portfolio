package com.pixelwms;

import com.pixelwms.fleet.FleetClientProperties;
import com.pixelwms.mqtt.MqttProperties;
import com.pixelplatform.core.auth.jwt.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * pixel-wms — 창고관리(WMS) 모듈. "로봇이 왜 움직이는가"에 답한다: 출고지시가 fleet에 운송을
 * 요청하고, 운송 완료를 MQTT로 받아 재고를 차감한다. 표준CT(품목×공정)의 마스터도 여기 있다.
 *
 * <p>공통 코어(common/auth/user)는 {@code com.pixelplatform.core}에 있으므로 스캔 범위를
 * 두 패키지로 명시한다(factory/fleet과 동일).
 */
@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.pixelwms", "com.pixelplatform.core"})
@EntityScan(basePackages = {"com.pixelwms", "com.pixelplatform.core"})
@EnableJpaRepositories(basePackages = {"com.pixelwms", "com.pixelplatform.core"})
@EnableConfigurationProperties({JwtProperties.class, MqttProperties.class, FleetClientProperties.class})
public class WmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }
}
