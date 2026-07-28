package com.pixelfactory.user;

import com.pixelplatform.core.user.domain.User;
import com.pixelplatform.core.user.domain.UserRole;
import com.pixelplatform.core.user.repository.UserRepository;
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
                new User("admin", encodedPassword, "관리자", UserRole.ADMIN, "생산관리"),
                new User("inspector", encodedPassword, "검사 담당자", UserRole.INSPECTOR, "품질"),
                new User("operator", encodedPassword, "작업자", UserRole.OPERATOR, "생산")
        ));
        log.info("Seeded {} demo users (password: '{}').", userRepository.count(), DEMO_PASSWORD);
    }
}
