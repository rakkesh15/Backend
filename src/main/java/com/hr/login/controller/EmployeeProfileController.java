package com.hr.login.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hr.login.entity.EmployeeProfile;
import com.hr.login.service.EmployeeProfileService;

@RestController
@RequestMapping("/api/employee-profiles")
public class EmployeeProfileController {

    @Autowired
    EmployeeProfileService service;

    @GetMapping("/{employeeCode}")
    public EmployeeProfile getProfile(
            @PathVariable String employeeCode
    ) {

        return service.getProfile(employeeCode);
    }

    @PostMapping
    public EmployeeProfile save(
            @RequestBody EmployeeProfile profile
    ) {

        return service.save(profile);
    }
}
