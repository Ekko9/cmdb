package com.cmdb.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {
    @Test
    void preservesAuthenticationStatus() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        ResponseEntity<?> response = handler.handleStatus(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
