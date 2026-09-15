package com.cmdb.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleConstraint() {
        return message("数据保存失败，请检查唯一字段或关联数据");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handle(Exception exception) {
        return message(exception.getMessage() == null ? "请求处理失败" : exception.getMessage());
    }

    private Map<String, String> message(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("message", value);
        return result;
    }
}
