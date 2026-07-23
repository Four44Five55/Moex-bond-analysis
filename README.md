# MOEX Analytics — анализ облигаций и акций

Веб-приложение для анализа инструментов Московской биржи (облигации и акции):
детальный анализ, поиск, фильтрация, графики исторических цен, избранное и сравнение.

## Стек

- **Backend:** Java 21, Spring Boot 3.4 (Web, Data JPA, Data Redis, Cache, Validation), PostgreSQL 16,
  Redis 7, Flyway, OkHttp, Apache POI, springdoc/OpenAPI. Тесты: JUnit 5 + Testcontainers.
- **Frontend:** React 18 + TypeScript + Vite, TanStack Query, React Router, lightweight-charts.
- **Инфраструктура:** Docker Compose (Postgres + Redis).

## Архитектура (backend, слоистая / hexagonal-lite)

```
web            REST-контроллеры v1, DTO (records), маппер, обработчик ошибок, OpenAPI
application    use-cases/фасады: InstrumentService, SyncService, CandleService,
               WatchlistService, ExportService; события (Observer)
domain         модель (Instrument→Bond/Share, Candle, WatchlistItem), анализ,
               фильтры (Specification), порты (MarketDataProvider, CandleProvider)
infrastructure адаптеры: moex/* (клиент+парсеры), persistence/* (Spring Data JPA),
               cache/* (Redis), scheduler/* (синхронизация)
```

**SOLID:** SRP (парсер/анализатор/репозиторий/контроллер раздельно), OCP (новый тип = новая стратегия),
LSP (Bond/Share ↔ Instrument), ISP (узкие порты), DIP (домен зависит от портов, реализации — в infrastructure).

**Паттерны GoF:** Strategy (парсеры/скоринг по типу), Abstract Factory / Adapter (MOEX-клиент),
Template Method (поток загрузки MOEX), Repository + Specification (динамические фильтры),
Facade (InstrumentService), Observer (`DataRefreshedEvent`), Decorator/Proxy (Spring Cache поверх портов).

**Конкурентность:** облигации и акции загружаются параллельно (`CompletableFuture` + выделенный пул),
свечи кешируются (cache-aside: PostgreSQL → MOEX), горячие ответы — в Redis; планировщик `@Scheduled`
периодически синхронизирует данные и сбрасывает кеши через событие.

## Запуск

Порты подобраны без конфликтов: backend **8090**, frontend dev **5180**, Postgres **5445**, Redis **6380**.

```bash
# 1. Инфраструктура
docker compose up -d

# 2. Backend (http://localhost:8090, Swagger UI: /swagger-ui.html)
cd backend
./gradlew bootRun        # при пустой БД выполнит стартовую синхронизацию с MOEX

# 3. Frontend (http://localhost:5180)
cd frontend
npm install
npm run dev
```

## REST API (`/api/v1`)

| Метод | Путь | Описание |
|-------|------|----------|
| GET  | `/instruments?type=BOND\|SHARE&query=&minYield=&maxDuration=&minReliability=&onlyInvestmentGrade=&sector=&page=&size=&sort=` | Список с фильтрами, пагинацией, сортировкой; для облигаций — сводка |
| GET  | `/instruments/search?query=` | Глобальный поиск по облигациям и акциям |
| GET  | `/instruments/{secId}` | Детальная карточка |
| GET  | `/instruments/{secId}/candles?interval=24&from=&to=` | Исторические свечи |
| GET  | `/instruments/compare?secIds=SBER,GAZP,...` | Данные для сравнения |
| POST | `/instruments/refresh` | Синхронизация с MOEX (облигации + акции) |
| GET  | `/export?type=&...фильтры` | Экспорт выборки в Excel (.xlsx) |
| GET/POST/DELETE | `/watchlist` (заголовок `X-Client-Id`) | Анонимное избранное |

## Тесты

```bash
cd backend && ./gradlew test     # unit + Testcontainers (реальный PostgreSQL)
cd frontend && npm run build     # проверка типов + сборка
```

## Замечание по данным

У MOEX ISS нет прямого кредитного рейтинга — надёжность облигаций оценивается прокси-эвристикой
(уровень листинга + признак ОФЗ) в `ReliabilityScorer`; заменяется на реальный источник без изменения
остального кода. Аномальные доходности (YTM в сотни %) помечаются флагом `suspiciousYield` и по умолчанию
исключаются из выборок и сводок.
