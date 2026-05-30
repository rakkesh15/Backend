package com.hr.login.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hr.login.entity.EmployeeProfile;

public interface EmployeeProfileRepository
        extends JpaRepository<EmployeeProfile, Integer> {

    EmployeeProfile findByEmployeeCode(String employeeCode);
}
