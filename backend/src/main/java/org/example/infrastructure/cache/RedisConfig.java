package org.example.infrastructure.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * Настройка кеша Redis: JSON-сериализация с сохранением типовой информации
 * (чтобы корректно восстанавливать record-DTO, даты и enum'ы).
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper());

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }

    /**
     * Ключевая ставка ЦБ меняется несколько раз в год, а не раз в пять минут: держать её
     * на общем TTL значило бы дёргать внешний SOAP-сервис сотни раз в сутки ради константы.
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer keyRateCacheTtl(RedisCacheConfiguration base) {
        return builder -> builder.withCacheConfiguration("keyRate",
                base.entryTtl(Duration.ofHours(12)));
    }

    /**
     * Признак дефолта выпуска меняется раз в жизни бумаги, а спрашивается на каждый пересчёт
     * портфеля — по запросу к MOEX на каждую облигацию. Пяти минут здесь мало.
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer bondDefaultCacheTtl(RedisCacheConfiguration base) {
        return builder -> builder.withCacheConfiguration("bondDefault",
                base.entryTtl(Duration.ofHours(6)));
    }

    /**
     * Записывает имя класса в JSON, иначе record-DTO, даты и enum'ы не восстанавливаются.
     * <p>
     * Список разрешённых пакетов ограничен намеренно: валидатор вида {@code allowIfSubType(Object.class)}
     * не ограничивает ничего и превращает содержимое кеша в инструкцию, какой класс инстанцировать.
     * Кешируются только DTO приложения и стандартные типы — их и разрешаем.
     */
    static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfSubType("org.example.")
                                .allowIfSubType("java.util.")
                                .allowIfSubType("java.time.")
                                .allowIfSubType("java.lang.")
                                .build(),
                        ObjectMapper.DefaultTyping.EVERYTHING,
                        JsonTypeInfo.As.PROPERTY);
    }
}
