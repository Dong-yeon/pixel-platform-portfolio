package com.pixelqms;

import com.pixelqms.factory.FactoryClientProperties;
import com.pixelqms.mqtt.MqttProperties;
import com.pixelqms.notification.NotificationProperties;
import com.pixelplatform.core.auth.jwt.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * pixel-qms — 품질관리(QMS) 모듈.
 *
 * <p>검사 → 부적합(NCR) → MRB 심의로 이어지는 품질 프로세스를 갖고, MRB가 열리면 factory의
 * 설비를 홀드시킨다. <b>컴포저블 아키텍처를 화면으로 증명하는 지점</b>이다 — 별개 서비스·
 * 별개 DB인데 지도의 설비가 주황으로 변했다가 판정 후 돌아온다.
 */
@EnableJpaAuditing
@SpringBootApplication(scanBasePackages = {"com.pixelqms", "com.pixelplatform.core"})
@EntityScan(basePackages = {"com.pixelqms", "com.pixelplatform.core"})
@EnableJpaRepositories(basePackages = {"com.pixelqms", "com.pixelplatform.core"})
@EnableConfigurationProperties({
        JwtProperties.class, MqttProperties.class,
        FactoryClientProperties.class, NotificationProperties.class
})
public class QmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(QmsApplication.class, args);
    }
}
