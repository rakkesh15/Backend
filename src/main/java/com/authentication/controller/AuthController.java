package com.authentication.controller;

import com.authentication.dto.ChangePasswordRequest;
import com.authentication.dto.LoginRequest;
import com.authentication.dto.LoginResponse;
import com.authentication.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    @PostMapping("/change-password")
    public ResponseEntity<LoginResponse> changePassword(
            @RequestHeader("X-Employee-Code") String employeeCode,
            @RequestBody ChangePasswordRequest request
    ) {
        if (request.getNewPassword() != null
                && !request.getNewPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest()
                    .body(new LoginResponse("Passwords do not match.", null, false, null, null, null, null, null));
        }

        LoginResponse response = authService.changePassword(
                employeeCode,
                request.getCurrentPassword(),
                request.getNewPassword()
        );

        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        return ResponseEntity.ok(response);
    }
}
