package com.hr.login.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hr.login.entity.EmployeeProfile;
import com.hr.login.repository.EmployeeProfileRepository;

@Service
public class EmployeeProfileService {

    @Autowired
    EmployeeProfileRepository repository;

    public EmployeeProfile getProfile(String employeeCode) {
        return repository.findByEmployeeCode(employeeCode);
    }

    public EmployeeProfile save(EmployeeProfile profile) {
        return repository.save(profile);
    }
}