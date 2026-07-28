package com.pixelfleet.user;

import com.pixelfleet.user.domain.User;
import com.pixelfleet.user.domain.UserRole;
import com.pixelfleet.user.repository.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds demo accounts on an empty database. Passwords must be bcrypt-encoded,
 * so this runs in code instead of a Flyway SQL migration.
 */
@Component
public class UserDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserDataInitializer.class);
    private static final String DEMO_PASSWORD = "password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        String encodedPassword = passwordEncoder.encode(DEMO_PASSWORD);
        userRepository.saveAll(List.of(
                new User("admin", encodedPassword, "관리자", UserRole.ADMIN, "관제"),
                new User("dispatcher", encodedPassword, "배차 담당자", UserRole.DISPATCHER, "운영"),
                new User("operator", encodedPassword, "현장 작업자", UserRole.OPERATOR, "물류")
        ));
        log.info("Seeded {} demo users (password: '{}').", userRepository.count(), DEMO_PASSWORD);
    }
}
