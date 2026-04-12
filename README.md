# ab-platform

Платформа для feature flags и A/B-экспериментов с распределением пользователей, аналитикой, раскаткой, рисками и аудитом.

## Обзор

`ab-platform` покрывает основной цикл эксперимента:

- создание флага
- создание и управление экспериментом
- распределение значения флага для пользователя
- сбор и расчет метрик
- оценка результатов
- rollout и rollback
- аудит истории действий

Проект собран как модульный монолит и разделен на отдельные доменные модули:

- `modules/feature-flag`
- `modules/experiment`
- `modules/assignment`
- `modules/analytics`

А также общие модули:

- `shared/common`
- `shared/contracts`
- `shared/caching`
- `shared/locking`
- `shared/testing`

## Схемы

### Flow работы платформы

<img width="2431" height="911" alt="image" src="https://github.com/user-attachments/assets/32d871b4-2846-4fa3-97e6-a422a031639b" />

<img width="1871" height="1104" alt="image" src="https://github.com/user-attachments/assets/380253b2-73c5-4969-ad77-d3f8df7e75b6" />

### Cхема таблиц базы данных

<img width="1965" height="1165" alt="database" src="https://github.com/user-attachments/assets/980c451d-08fa-471f-b60d-b9a09847759e" />

## API

<img width="1759" height="890" alt="api" src="https://github.com/user-attachments/assets/46706ffb-dc28-46f3-b228-d71d2325a547" />

## Безопасность

Платформа использует внутренний токен и ролевую модель:

- `VIEWER`
- `EXPERIMENTER`
- `OPERATOR`

## Быстрый старт

### Настройка окружения

Примеры основных переменных:

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `AB_AUTH_INTERNAL_TOKEN_ISSUER`

### Запуск

```bash
docker compose up -d
```

## Структура проекта

```text
app/
modules/
  feature-flag/
  experiment/
  assignment/
  analytics/
shared/
  common/
  contracts/
  caching/
  locking/
  testing/
```
