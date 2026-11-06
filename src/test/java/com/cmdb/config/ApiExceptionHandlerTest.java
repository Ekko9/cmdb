package com.cmdb.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiExceptionHandlerTest {
    @Test
    void preservesAuthenticationStatus() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        ResponseEntity<?> response = handler.handleStatus(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void mapsExpectedErrorsWithoutExposingUnexpectedFailures() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        assertEquals("输入无效", handler.handleBadRequest(new BadRequestException("输入无效")).get("message"));
        assertEquals("资源不存在", handler.handleNotFound(new ResourceNotFoundException("资源不存在")).get("message"));
        assertEquals("资源不存在", handler.handleStaticNotFound().get("message"));
        assertEquals("服务暂时不可用，请稍后重试", handler.handle(new IllegalStateException("secret detail")).get("message"));
    }
}
