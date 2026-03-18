package com.procurehub.auth.api;

import com.procurehub.auth.dto.AuthResponse;
import com.procurehub.auth.dto.LoginRequest;
import com.procurehub.auth.dto.RefreshTokenRequest;
import com.procurehub.auth.dto.RegisterRequest;
import com.procurehub.auth.dto.RoleUpdateRequest;
import com.procurehub.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public Map<String, Object> me(Authentication authentication) {
        return Map.of(
                "email", authentication.getName(),
                "roles", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList()
        );
    }

    @GetMapping("/admin/ping")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> adminPing() {
        return Map.of("status", "admin-ok");
    }

    @PostMapping("/admin/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> changeRole(@Valid @RequestBody RoleUpdateRequest request) {
        authService.changeUserRole(request.getEmail(), request.getRole());
        return Map.of("status", "role-updated");
    }

    @GetMapping("/confirm")
    public Map<String, String> confirm(@RequestParam("token") String token) {
        authService.confirmEmail(token);
        return Map.of("status", "email-confirmed");
    }
}
