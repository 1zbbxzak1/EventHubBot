package ru.unithack.bot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.unithack.bot.domain.enums.UserRole;
import ru.unithack.bot.domain.model.Role;
import ru.unithack.bot.domain.model.RoleUser;
import ru.unithack.bot.infrastructure.repository.RoleRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private final RoleRepository roleRepository;

    @Autowired
    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional
    public Role getOrCreateRole(UserRole userRole) {
        Optional<Role> optionalRole = roleRepository.findByName(userRole.name());
        if (optionalRole.isPresent()) {
            return optionalRole.get();
        } else {
            Role role = new Role(userRole.name());
            return roleRepository.save(role);
        }
    }

    @Transactional(readOnly = true)
    public boolean hasRole(Long userId, UserRole userRole) {
        return roleRepository.findByName(userRole.name())
                .map(role -> role.getRoleUser().stream()
                        .anyMatch(roleUser -> roleUser.getUser().getId().equals(userId)))
                .orElse(false);
    }
    
    @Transactional(readOnly = true)
    public Set<String> getUserRoles(Long userId) {
        return roleRepository.findAll().stream()
                .filter(role -> role.getRoleUser().stream()
                        .anyMatch(roleUser -> roleUser.getUser().getId().equals(userId)))
                .map(Role::getName)
                .collect(Collectors.toSet());
    }
} 