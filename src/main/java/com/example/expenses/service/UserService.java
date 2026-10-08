package com.example.expenses.service;

import com.example.expenses.model.AuditAction;
import com.example.expenses.model.User;
import com.example.expenses.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public User register(String username, String rawPassword) {
        String normalized = User.normalize(username);
        if (userRepository.existsByUsername(normalized)) {
            throw new UsernameTakenException(normalized);
        }
        User user = userRepository.save(new User(normalized, passwordEncoder.encode(rawPassword)));
        auditService.record(user.getId(), AuditAction.REGISTERED, "Username " + normalized);
        return user;
    }
}
