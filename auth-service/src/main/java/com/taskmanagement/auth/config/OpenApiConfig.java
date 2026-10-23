package com.taskmanagement.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Auth Service API", version = "1.0.0",
        description = "User registration, login and JWT (RS256) issuing."))
public class OpenApiConfig {
}
