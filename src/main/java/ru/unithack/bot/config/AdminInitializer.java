package ru.unithack.bot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.unithack.bot.domain.enums.UserRole;
import ru.unithack.bot.domain.model.User;
import ru.unithack.bot.infrastructure.repository.UserRepository;
import ru.unithack.bot.service.UserService;

import java.util.Optional;
import java.util.UUID;

@Configuration
public class AdminInitializer {

    private static final Logger logger = LoggerFactory.getLogger(AdminInitializer.class);

    @Value("${app.admin.name:Admin}")
    private String adminName;

    @Value("${app.admin.qr-code:admin123}")
    private String adminQrCode;

    @Value("${app.admin.chat-id}")
    private Long adminChatId;

    private final UserService userService;
    private final UserRepository userRepository;

    @Autowired
    public AdminInitializer(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Bean
    public CommandLineRunner initializeAdmin() {
        return args -> {
            Optional<User> existingAdmin = userRepository.findByUserInfo_ChatId(adminChatId);

            if (existingAdmin.isEmpty()) {
                String qrCode = UUID.randomUUID().toString();
                User adminUser = userService.createUserWithRole(adminName, qrCode, adminChatId, UserRole.ADMIN);
                logger.info("Admin user created successfully with ID: {}", adminUser.getId());
            } else {
                logger.info("Admin user already exists with ID: {}", existingAdmin.get().getId());
                userService.addRoleToUser(existingAdmin.get().getId(), UserRole.ADMIN);
            }
        };
    }
} 