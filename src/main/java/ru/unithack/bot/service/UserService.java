package ru.unithack.bot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.unithack.bot.domain.enums.UserRole;
import ru.unithack.bot.domain.model.Role;
import ru.unithack.bot.domain.model.RoleUser;
import ru.unithack.bot.domain.model.User;
import ru.unithack.bot.domain.model.UserInfo;
import ru.unithack.bot.infrastructure.repository.UserRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleService roleService;

    @Autowired
    public UserService(UserRepository userRepository, RoleService roleService) {
        this.userRepository = userRepository;
        this.roleService = roleService;
    }

    @Transactional
    public User createUserWithRole(String name, String qrCode, Long chatId, UserRole userRole) {
        User user = new User();
        userRepository.save(user);

        UserInfo userInfo = new UserInfo(user, ZonedDateTime.now());
        userInfo.setName(name);
        userInfo.setQrCode(qrCode);
        userInfo.setChatId(chatId);

        Role role = roleService.getOrCreateRole(userRole);
        new RoleUser(user, role);

        return userRepository.save(user);
    }

    @Transactional
    public User updateUserName(Long chatId, String newName) {
        return userRepository.findByUserInfo_ChatId(chatId)
                .map(user -> {
                    user.getUserInfo().setName(newName);
                    return userRepository.save(user);
                })
                .orElseThrow(() -> new IllegalArgumentException("User not found with chatId: " + chatId));
    }

    @Transactional
    public boolean addRoleToUser(Long userId, UserRole userRole) {
        return userRepository.findById(userId).map(user -> {
            Role role = roleService.getOrCreateRole(userRole);

            boolean hasRole = user.getRoleUser().stream()
                    .anyMatch(roleUser -> roleUser.getRole().getName().equals(userRole.name()));

            if (!hasRole) {
                new RoleUser(user, role);
                userRepository.save(user);
            }

            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean addRoleToUserByName(String name, UserRole userRole) {
        return userRepository.findByUserInfo_Name(name)
                .map(user -> {
                    Role role = roleService.getOrCreateRole(userRole);

                    boolean hasRole = user.getRoleUser().stream()
                            .anyMatch(roleUser -> roleUser.getRole().getName().equals(userRole.name()));

                    if (!hasRole) {
                        new RoleUser(user, role);
                        userRepository.save(user);
                    }

                    return true;
                }).orElse(false);
    }

    @Transactional
    public boolean addRoleToUserByChatId(Long chatId, UserRole userRole) {
        return userRepository.findByUserInfo_ChatId(chatId)
                .map(user -> {
                    Role role = roleService.getOrCreateRole(userRole);

                    boolean hasRole = user.getRoleUser().stream()
                            .anyMatch(roleUser -> roleUser.getRole().getName().equals(userRole.name()));

                    if (!hasRole) {
                        new RoleUser(user, role);
                        userRepository.save(user);
                    }

                    return true;
                }).orElse(false);
    }

    @Transactional
    public boolean removeRoleFromUserByChatId(Long chatId, UserRole userRole) {
        return userRepository.findByUserInfo_ChatId(chatId)
                .map(user -> {
                    List<RoleUser> rolesToRemove = user.getRoleUser().stream()
                            .filter(roleUser -> roleUser.getRole().getName().equals(userRole.name()))
                            .toList();

                    if (!rolesToRemove.isEmpty()) {
                        for (RoleUser roleUser : rolesToRemove) {
                            user.getRoleUser().remove(roleUser);
                            roleUser.setUser(null);
                            roleUser.setRole(null);
                        }
                        userRepository.save(user);
                        return true;
                    }

                    return false;
                }).orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserByChatId(Long chatId) {
        return userRepository.findByUserInfo_ChatId(chatId);
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserByName(String name) {
        return userRepository.findByUserInfo_Name(name);
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public boolean hasRole(Long userId, UserRole userRole) {
        return roleService.hasRole(userId, userRole);
    }
} 