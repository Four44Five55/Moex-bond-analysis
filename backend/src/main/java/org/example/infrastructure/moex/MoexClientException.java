package org.example.infrastructure.moex;

/** Ошибка обращения к MOEX ISS (сетевые сбои, неверная структура ответа). */
public class MoexClientException extends RuntimeException {
    public MoexClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public MoexClientException(String message) {
        super(message);
    }
}
