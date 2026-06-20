package com.authentication.service;

import com.authentication.dto.LoginRequest;
import com.authentication.dto.LoginResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        String email = request == null ? null : normalize(request.getEmail());
        String password = request == null ? null : request.getPassword();

        if (email == null || password == null || password.isBlank()) {
            return fail("Email and password are required.");
        }

        UserLogin user = findUserByEmail(email);
        if (user == null || !matchesPassword(password, user.passwordHash())) {
            return fail("Invalid email or password.");
        }

        if (Boolean.FALSE.equals(user.isAlive())) {
            return fail("Your account has been deactivated. Please contact HR.");
        }

        updateLastActive(user.id());

        String role = normalizeRole(user.role());
        String employeeCode = user.employeeCode();
        String userEmail = user.username();

        return new LoginResponse(
                "Login successful",
                role,
                true,
                userEmail,
                fullNameFor(employeeCode, userEmail),
                employeeCode,
                tokenFor(employeeCode, role),
                redirectUrl(role)
        );
    }

    public LoginResponse changePassword(String employeeCode, String currentPassword, String newPassword) {
        String code = normalize(employeeCode);
        if (code == null) {
            return fail("Employee code is required.");
        }

        UserLogin user = findUserByEmployeeCode(code);
        if (user == null) {
            return fail("User not found.");
        }

        if (!matchesPassword(currentPassword, user.passwordHash())) {
            return fail("Current password is incorrect.");
        }

        if (newPassword == null || newPassword.length() < 8) {
            return fail("New password must be at least 8 characters.");
        }

        jdbcTemplate.update(
                "update users set password_hash = ? where employee_code = ?",
                passwordEncoder.encode(newPassword),
                user.employeeCode()
        );

        String role = normalizeRole(user.role());
        return new LoginResponse(
                "Password changed successfully.",
                role,
                true,
                user.username(),
                fullNameFor(user.employeeCode(), user.username()),
                user.employeeCode(),
                null,
                redirectUrl(role)
        );
    }

    private UserLogin findUserByEmail(String email) {
        try {
            return jdbcTemplate.queryForObject(
                    "select id, employee_code, username, password_hash, role, is_alive " +
                            "from users " +
                            "where lower(username) = lower(?) " +
                            "limit 1",
                    (rs, rowNum) -> new UserLogin(
                            rs.getLong("id"),
                            rs.getString("employee_code"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("role"),
                            rs.getObject("is_alive") == null || rs.getBoolean("is_alive")
                    ),
                    email
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private UserLogin findUserByEmployeeCode(String employeeCode) {
        try {
            return jdbcTemplate.queryForObject(
                    "select id, employee_code, username, password_hash, role, is_alive " +
                            "from users " +
                            "where lower(employee_code) = lower(?) " +
                            "limit 1",
                    (rs, rowNum) -> new UserLogin(
                            rs.getLong("id"),
                            rs.getString("employee_code"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("role"),
                            rs.getObject("is_alive") == null || rs.getBoolean("is_alive")
                    ),
                    employeeCode
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private void updateLastActive(Long userId) {
        try {
            jdbcTemplate.update("update users set last_active = current_timestamp where id = ?", userId);
        } catch (RuntimeException ignored) {
            // Some existing databases do not have last_active yet; login should still work.
        }
    }

    private boolean matchesPassword(String raw, String stored) {
        if (raw == null || stored == null || stored.isBlank()) {
            return false;
        }

        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            try {
                return passwordEncoder.matches(raw, stored);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }

        return raw.equals(stored);
    }

    private String fullNameFor(String employeeCode, String fallback) {
        if (employeeCode == null || employeeCode.isBlank()) {
            return fallback;
        }

        for (String sql : new String[]{
                "select full_name from employee_profiles where employee_code = ?",
                "select name from employees where id = ?"
        }) {
            try {
                String fullName = jdbcTemplate.queryForObject(sql, String.class, employeeCode);
                if (fullName != null && !fullName.isBlank()) {
                    return fullName;
                }
            } catch (RuntimeException ignored) {
                // Fall through to the next known employee table shape.
            }
        }

        return fallback;
    }

    private String redirectUrl(String role) {
        return switch (normalizeRole(role)) {
            case "ADMIN", "HR" -> "admin_hr/admin_hr_dashboard/code.html";
            case "MANAGER", "MANAGEMENT" -> "management/management_dashboard/code.html";
            default -> "employee/employee_dashboard/code.html";
        };
    }

    private String tokenFor(String employeeCode, String role) {
        String value = employeeCode + ":" + role + ":" + Instant.now().toEpochMilli();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "EMPLOYEE";
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private LoginResponse fail(String message) {
        return new LoginResponse(message, null, false, null, null, null, null, null);
    }

    private record UserLogin(
            Long id,
            String employeeCode,
            String username,
            String passwordHash,
            String role,
            Boolean isAlive
    ) {
    }
}
