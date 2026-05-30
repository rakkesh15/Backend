package com.hr.login.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Employee Management API",
                version = "v1",
                description = "Backend API for employee profile, dashboard, attendance, leave, performance, and notifications.",
                contact = @Contact(name = "Employee Management")
        )
)
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi employeeApi() {
        return GroupedOpenApi.builder()
                .group("employee-api")
                .pathsToMatch("/api/**")
                .build();
    }
}
