package com.hr.login.dto;

public class LoginResponse {

    private String message;

    private String role;

    private boolean success;

    private String email;

    private String employeeCode;

    // ================= CONSTRUCTOR =================

    public LoginResponse(
            String message,
            String role,
            boolean success,
            String email,
            String employeeCode
    ) {

        this.message = message;
        this.role = role;
        this.success = success;
        this.email = email;
        this.employeeCode = employeeCode;
    }

    // ================= GETTERS =================

    public String getMessage() {
        return message;
    }

    public String getRole() {
        return role;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getEmail() {
        return email;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    // ================= SETTERS =================

    public void setMessage(String message) {
        this.message = message;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setEmployeeCode(
            String employeeCode
    ) {

        this.employeeCode =
                employeeCode;
    }
}