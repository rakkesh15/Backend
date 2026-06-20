package com.employee.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees/{employeeCode}")
public class EmployeeDashboardApiController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(
            @PathVariable String employeeCode
    ) {

        Map<String, Object> data = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        data.put("profile", profile(employeeCode));
        data.put("todayAttendance", one(
                "select * from attendance_records where employee_code = ? and work_date = ?",
                employeeCode,
                today
        ));
        data.put("todayBreaks", list(
                "select * from attendance_break_logs where employee_code = ? and work_date = ? order by break_start",
                employeeCode,
                today
        ));
        data.put("leaveBalance", leaveBalance(employeeCode));
        data.put("payroll", latestPayslip(employeeCode));
        data.put("pendingLeaveDays", scalarInt(
                "select coalesce(sum(number_of_days), 0) from leave_requests where employee_code = ? and status = 'Pending'",
                employeeCode
        ));
        data.put("tasks", list(
                "select * from priority_tasks where employee_code = ? order by due_date limit 3",
                employeeCode
        ));
        data.put("feedback", list(
                "select * from manager_feedback where employee_code = ? order by review_date desc limit 2",
                employeeCode
        ));
        data.put("unreadNotifications", scalarInt(
                "select count(*) from in_app_notifications where employee_code = ? and is_read = false",
                employeeCode
        ));

        return data;
    }

    @GetMapping("/attendance")
    public Map<String, Object> attendance(
            @PathVariable String employeeCode
    ) {

        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);

        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> records = list(
                "select * from attendance_records where employee_code = ? and work_date >= ? order by work_date desc",
                employeeCode,
                monthStart
        );

        data.put("records", records);
        data.put("presentDays", records.stream()
                .filter(row -> !"Absent".equals(row.get("status")))
                .count());
        data.put("lateDays", records.stream()
                .filter(row -> "Late".equals(row.get("status")))
                .count());
        data.put("averageCheckIn", oneValue(
                "select date_format(sec_to_time(avg(time_to_sec(check_in))), '%h:%i %p') from attendance_records where employee_code = ? and work_date >= ? and check_in is not null",
                employeeCode,
                monthStart
        ));
        data.put("currentStreak", currentStreak(employeeCode));

        return data;
    }

    @PostMapping("/attendance/check-in")
    public ResponseEntity<Map<String, Object>> checkIn(
            @PathVariable String employeeCode
    ) {

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now().withNano(0);
        String status = now.isAfter(LocalTime.of(9, 15))
                ? "Late"
                : "On Time";

        jdbcTemplate.update(
                "insert into attendance_records (employee_code, work_date, check_in, status) values (?, ?, ?, ?) " +
                        "on duplicate key update check_in = coalesce(check_in, values(check_in)), status = if(check_in is null, values(status), status)",
                employeeCode,
                today,
                now,
                status
        );

        return ResponseEntity.ok(attendance(employeeCode));
    }

    @PostMapping("/attendance/check-out")
    public ResponseEntity<Map<String, Object>> checkOut(
            @PathVariable String employeeCode
    ) {

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now().withNano(0);

        jdbcTemplate.update(
                "update attendance_records set check_out = ?, total_hours = round(timestampdiff(minute, timestamp(work_date, check_in), timestamp(work_date, ?)) / 60, 2) where employee_code = ? and work_date = ? and check_in is not null",
                now,
                now,
                employeeCode,
                today
        );

        return ResponseEntity.ok(attendance(employeeCode));
    }

    @GetMapping("/leave")
    public Map<String, Object> leave(
            @PathVariable String employeeCode
    ) {

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("balance", leaveBalance(employeeCode));
        data.put("requests", list(
                "select * from leave_requests where employee_code = ? order by created_at desc",
                employeeCode
        ));

        return data;
    }

    @PostMapping("/leave")
    public ResponseEntity<?> applyLeave(
            @PathVariable String employeeCode,
            @RequestBody Map<String, String> request
    ) {

        String leaveType = request.getOrDefault("leaveType", "").trim();
        LocalDate startDate = LocalDate.parse(request.get("startDate"));
        LocalDate endDate = LocalDate.parse(request.get("endDate"));
        String reason = request.getOrDefault("reason", "").trim();

        if (leaveType.isBlank() || reason.isBlank() || endDate.isBefore(startDate)) {
            return ResponseEntity.badRequest()
                    .body("Please provide a valid leave type, date range, and reason.");
        }

        int numberOfDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;

        jdbcTemplate.update(
                "insert into leave_requests (employee_code, leave_type, start_date, end_date, number_of_days, reason) values (?, ?, ?, ?, ?, ?)",
                employeeCode,
                leaveType,
                startDate,
                endDate,
                numberOfDays,
                reason
        );

        return ResponseEntity.ok(leave(employeeCode));
    }

    @GetMapping("/performance")
    public Map<String, Object> performance(
            @PathVariable String employeeCode
    ) {

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("goals", list(
                "select * from performance_goals where employee_code = ? order by quarter desc, updated_at desc",
                employeeCode
        ));
        data.put("feedback", list(
                "select * from manager_feedback where employee_code = ? order by review_date desc",
                employeeCode
        ));
        data.put("rating", oneValue(
                "select round(avg(rating_score), 1) from manager_feedback where employee_code = ? and rating_score is not null",
                employeeCode
        ));

        return data;
    }

    @GetMapping("/profile")
    public Map<String, Object> profilePage(
            @PathVariable String employeeCode
    ) {

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("profile", profile(employeeCode));
        List<Map<String, Object>> documents = list(
                "SELECT id, document_name, document_type, uploaded_at " +
                "FROM employee_documents " +
                "WHERE employee_code = ? " +
                "ORDER BY uploaded_at DESC",
                employeeCode
        );

        documents.forEach(doc -> {
            doc.put(
                    "downloadUrl",
                    "/api/employees/" +
                    employeeCode +
                    "/documents/" +
                    doc.get("id") +
                    "/download"
            );
        });

        data.put("documents", documents);
        data.put("preferences", preferences(employeeCode));

        return data;
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @PathVariable String employeeCode,
            @RequestBody Map<String, String> request
    ) {

        jdbcTemplate.update(
                "update employee_profiles set full_name = ?, photo_url = ?, phone_number = ?, date_of_birth = ?, residential_address = ?, emergency_contact_name = ?, emergency_contact_phone = ? where employee_code = ?",
                request.get("fullName"),
                request.get("photoUrl"),
                request.get("phoneNumber"),
                emptyToNull(request.get("dateOfBirth")),
                request.get("residentialAddress"),
                request.get("emergencyContactName"),
                request.get("emergencyContactPhone"),
                employeeCode
        );

        String email = request.getOrDefault("email", "").trim();

        if (!email.isBlank()) {
            jdbcTemplate.update(
                    "update users set username = ? where employee_code = ?",
                    email,
                    employeeCode
            );
        }

        return ResponseEntity.ok(profilePage(employeeCode));
    }

    @PostMapping("/profile-photo")
    public ResponseEntity<?> uploadProfilePhoto(
            @PathVariable String employeeCode,
            @RequestParam("photo") MultipartFile photo
    ) throws IOException {

        if (photo.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Please select a profile photo.");
        }

        String contentType = photo.getContentType();

        if (contentType == null ||
                !contentType.startsWith("image/")) {

            return ResponseEntity.badRequest()
                    .body("Only image files are allowed.");
        }

        byte[] imageBytes = photo.getBytes();

        jdbcTemplate.update(
                "INSERT INTO employee_profile_photos " +
                "(employee_code, photo_name, content_type, photo_data, is_active) " +
                "VALUES (?, ?, ?, ?, false)",
                employeeCode,
                photo.getOriginalFilename(),
                contentType,
                imageBytes
        );

        return ResponseEntity.ok("Profile photo uploaded.");
    }

    @PostMapping("/documents")
    public ResponseEntity<?> uploadDocument(
            @PathVariable String employeeCode,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentType", defaultValue = "General") String documentType
    ) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Please select a document.");
        }

        String extension = documentExtension(file.getOriginalFilename());

        if (extension == null) {
            return ResponseEntity.badRequest()
                    .body("Only PDF, DOC, DOCX, JPG, JPEG, and PNG files are allowed.");
        }

        String originalName = safeDisplayName(
                file.getOriginalFilename(),
                "Document" + extension
        );

        byte[] fileBytes = file.getBytes();

        jdbcTemplate.update(
                "INSERT INTO employee_documents " +
                "(employee_code, document_name, document_type, content_type, file_data) " +
                "VALUES (?, ?, ?, ?, ?)",
                employeeCode,
                originalName,
                emptyToNull(documentType),
                file.getContentType(),
                fileBytes
        );

        return ResponseEntity.ok(profilePage(employeeCode));
    }
    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable String employeeCode,
            @PathVariable Long documentId
    ) {

        Map<String, Object> document = one(
                "SELECT * FROM employee_documents " +
                "WHERE id = ? AND employee_code = ?",
                documentId,
                employeeCode
        );

        if (document == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] fileData = (byte[]) document.get("file_data");

        String fileName =
                String.valueOf(document.get("document_name"));

        String contentType =
                String.valueOf(document.get("content_type"));

        return ResponseEntity.ok()
                .header(
                        "Content-Disposition",
                        "attachment; filename=\"" + fileName + "\""
                )
                .header(
                        "Content-Type",
                        contentType
                )
                .body(fileData);
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String employeeCode,
            @PathVariable Long documentId
    ) {

        jdbcTemplate.update(
                "DELETE FROM employee_documents " +
                "WHERE id = ? AND employee_code = ?",
                documentId,
                employeeCode
        );

        return ResponseEntity.ok(profilePage(employeeCode));
    }

    @PutMapping("/preferences")
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @PathVariable String employeeCode,
            @RequestBody Map<String, Object> request
    ) {

        jdbcTemplate.update(
                "insert into notification_preferences (employee_code, notify_leave_status, notify_payslip, notify_performance_reminders, notify_announcements, notify_attendance_reminders, digest_frequency) " +
                        "values (?, ?, ?, ?, ?, ?, ?) on duplicate key update notify_leave_status = values(notify_leave_status), notify_payslip = values(notify_payslip), notify_performance_reminders = values(notify_performance_reminders), notify_announcements = values(notify_announcements), notify_attendance_reminders = values(notify_attendance_reminders), digest_frequency = values(digest_frequency)",
                employeeCode,
                bool(request.get("notifyLeaveStatus")),
                bool(request.get("notifyPayslip")),
                bool(request.get("notifyPerformanceReminders")),
                bool(request.get("notifyAnnouncements")),
                bool(request.get("notifyAttendanceReminders")),
                request.getOrDefault("digestFrequency", "realtime")
        );

        return ResponseEntity.ok(profilePage(employeeCode));
    }

    @GetMapping("/notifications")
    public Map<String, Object> notifications(
            @PathVariable String employeeCode
    ) {

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("today", list(
                "select * from in_app_notifications where employee_code = ? and time_category = 'TODAY' order by created_at desc",
                employeeCode
        ));
        data.put("thisWeek", list(
                "select * from in_app_notifications where employee_code = ? and time_category = 'THIS WEEK' order by created_at desc",
                employeeCode
        ));

        return data;
    }

    /**
     * GET /api/employees/{employeeCode}/payslips
     * Returns payslip records for the employee ordered by payment date descending.
     * Queries the `payslips` table — falls back gracefully if the table doesn't exist yet.
     */
    @GetMapping("/payslips")
    public ResponseEntity<List<Map<String, Object>>> payslips(
            @PathVariable String employeeCode
    ) {
        try {
            List<Map<String, Object>> rows = list(
                    "select id, employee_code, payroll_month as pay_period, generated_at as payment_date, " +
                    "basic_salary, hra, allowances as special_allowance, " +
                    "(basic_salary + hra + allowances) as gross_salary, " +
                    "pf_deduction as pf_amount, tax_deduction as tds, other_deductions, " +
                    "(pf_deduction + tax_deduction + other_deductions) as total_deductions, " +
                    "net_salary, status, generated_at as created_at " +
                    "from payslips " +
                    "where employee_code = ? " +
                    "order by payroll_month desc",
                    employeeCode
            );
            return ResponseEntity.ok(rows);
        } catch (Exception ex) {
            // Table may not exist in all installations — return empty list instead of 500
            return ResponseEntity.ok(List.of());
        }
    }

    private Map<String, Object> profile(String employeeCode) {

        Map<String, Object> profile = one(
                "select ep.*, u.username as email from employee_profiles ep join users u on u.employee_code = ep.employee_code where ep.employee_code = ?",
                employeeCode
        );

        return profile == null
                ? new LinkedHashMap<>()
                : profile;
    }

    private Map<String, Object> leaveBalance(String employeeCode) {

        jdbcTemplate.update(
                "insert ignore into leave_balances (employee_code) values (?)",
                employeeCode
        );

        return one(
                "select * from leave_balances where employee_code = ?",
                employeeCode
        );
    }

    private Map<String, Object> preferences(String employeeCode) {

        jdbcTemplate.update(
                "insert ignore into notification_preferences (employee_code) values (?)",
                employeeCode
        );

        return one(
                "select * from notification_preferences where employee_code = ?",
                employeeCode
        );
    }

    private Map<String, Object> latestPayslip(String employeeCode) {
        try {
            return one(
                    "select id, employee_code, payroll_month as pay_period, generated_at as payment_date, " +
                    "basic_salary, hra, allowances as special_allowance, " +
                    "(basic_salary + hra + allowances) as gross_salary, " +
                    "pf_deduction as pf_amount, tax_deduction as tds, other_deductions, " +
                    "(pf_deduction + tax_deduction + other_deductions) as total_deductions, " +
                    "net_salary, status, generated_at as created_at " +
                    "from payslips where employee_code = ? order by payroll_month desc limit 1",
                    employeeCode
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private int currentStreak(String employeeCode) {

        List<Map<String, Object>> rows = list(
                "select work_date, status from attendance_records where employee_code = ? and status <> 'Absent' order by work_date desc",
                employeeCode
        );

        int streak = 0;
        LocalDate expected = LocalDate.now();

        for (Map<String, Object> row : rows) {
            Object dateValue = row.get("work_date");
            LocalDate workDate = dateValue instanceof LocalDate localDate
                    ? localDate
                    : ((java.sql.Date) dateValue).toLocalDate();

            if (workDate.equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (workDate.isBefore(expected)) {
                break;
            }
        }

        return streak;
    }

    private List<Map<String, Object>> list(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, args);
    }

    private Map<String, Object> one(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForMap(sql, args);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private Object oneValue(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Object.class, args);
    }

    private int scalarInt(String sql, Object... args) {
        Object value = oneValue(sql, args);

        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value instanceof BigDecimal decimal) {
            return decimal.intValue();
        }

        return 0;
    }

    private Object emptyToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value;
    }

    private boolean bool(Object value) {
        return Boolean.TRUE.equals(value) || "true".equals(String.valueOf(value));
    }

    private String extension(String fileName) {

        if (fileName == null || !fileName.contains(".")) {
            return ".jpg";
        }

        String extension = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();

        if (extension.matches("\\.(jpg|jpeg|png|webp|gif)")) {
            return extension;
        }

        return ".jpg";
    }

    private String documentExtension(String fileName) {

        if (fileName == null || !fileName.contains(".")) {
            return null;
        }

        String extension = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();

        if (extension.matches("\\.(pdf|doc|docx|jpg|jpeg|png)")) {
            return extension;
        }

        return null;
    }

    private String safeDisplayName(String fileName, String fallback) {

        if (fileName == null || fileName.isBlank()) {
            return fallback;
        }

        String cleaned = Paths.get(fileName).getFileName().toString()
                .replaceAll("[\\r\\n]", "")
                .trim();

        return cleaned.isBlank()
                ? fallback
                : cleaned;
    }
}
