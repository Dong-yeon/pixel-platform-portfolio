package com.pixelfactory;

import com.pixelfactory.auth.jwt.JwtProperties;
import com.pixelfactory.mqtt.MqttProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, MqttProperties.class})
public class PixelFactoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(PixelFactoryApplication.class, args);
    }
}
