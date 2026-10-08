package com.example.expenses.security;

import com.example.expenses.model.User;
import com.example.expenses.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Loads users for form login. Spring Security checks the BCrypt hash and the locked flag. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final Clock clock;

    public AppUserDetailsService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsername(User.normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return new AppUserDetails(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.isLocked(Instant.now(clock)));
    }
}
