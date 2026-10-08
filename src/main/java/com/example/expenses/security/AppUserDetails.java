package com.example.expenses.security;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * The logged-in user as Spring Security sees it. Controllers get it with
 * {@code @AuthenticationPrincipal} and pass {@link #getId()} to the services.
 */
public class AppUserDetails implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private String passwordHash;
    private final boolean locked;

    public AppUserDetails(Long id, String username, String passwordHash, boolean locked) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.locked = locked;
    }

    public Long getId() {
        return id;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    // Called after login so the hash isn't kept in the session
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
