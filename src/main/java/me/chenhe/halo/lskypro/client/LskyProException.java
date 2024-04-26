package me.chenhe.halo.lskypro.client;

import org.springframework.http.HttpStatusCode;

public class LskyProException extends RuntimeException {
    public HttpStatusCode statusCode;

    LskyProException(HttpStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}
