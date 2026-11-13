package com.example.aiterminal.exception;

import org.springframework.http.HttpStatus;

/** 具有明确 HTTP 状态码的业务错误。 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
