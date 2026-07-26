package org.example.infrastructure.cbr;

public class CbrClientException extends RuntimeException {

    public CbrClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public CbrClientException(String message) {
        super(message);
    }
}
