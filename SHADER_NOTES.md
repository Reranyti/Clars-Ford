# Визуальная совместимость Clars Ford (обновлено под v1.1010)

> **⚠ Скоро проект полностью изменится (теперь он называется Clars Ford, ранее Lotus Blight). Документ описывает старую версию и скоро устареет. / The project (now Clars Ford, formerly Lotus Blight) is about to change completely; this document describes the old version.**


## Streams Reflowing — интеграция реализована

Изначально рассматривался вопрос мягкой совместимости. Статус сейчас: реализовано и работает.
`STREAMS_REFLOWING_LOADED` в `InfectionSpreadEngine` реагирует на реальную скорость течения (через
патченный `getFlow()`), а не только на факт наличия воды — заражение по-настоящему быстрее следует
за течением реки, чем за стоячим озером, если Streams Reflowing установлен. Без него используется
обычное поведение вида воды.

## Complementary Shaders / Oculus — статус не менялся, рекомендация остаётся актуальной

Практическая связка для Forge 1.20.1 (Oculus на клиенте + Complementary Reimagined как shader pack)
не изменилась с исходной версии документа. Мод по-прежнему не полагается на шейдер для базовой
читаемости — цвета видны и без него, шейдер лишь подчёркивает воду и свечение.

Важное уточнение, добавленное после реального опыта с игроками: Embeddium (используемый частью
тестеров вместо ванильного рендера) явно НЕ поддерживает связку с Oculus официально — при
использовании обоих модов вместе игра сама предупреждает об этом при старте («Embeddium instance
tainted by mods: [oculus]»). Это не баг мода, но стоит иметь в виду при диагностике визуальных
проблем у тестеров с этой связкой модов: сначала исключить конфликт Embeddium/Oculus, прежде чем
искать причину в собственном коде мода.

## Собственная визуализация тумана — не было в исходном документе

Мод больше не полагается только на сторонние шейдеры для атмосферы. Реализована собственная
система тумана (`InfectionFogRenderer`, `QuarantineFogRenderer`): цвет тумана плавно меняется по
биому (через `BiomeFogColors`, свой градиент почти для каждого ванильного биома) и по близости к
активному очагу или карантинной границе — работает одинаково с шейдером и без него.

## References

[1]: https://modrinth.com/mod/streams-reflowing/versions — Streams Reflowing.
[2]: https://www.complementary.dev/shaders/ — Complementary Shaders.
[3]: https://modrinth.com/mod/oculus — Oculus.
[4]: https://modrinth.com/shader/complementary-reimagined/versions — Complementary Reimagined.

Актуализация 2026-09-24 — реальный код (`InfectionSpreadEngine`, `InfectionFogRenderer`,
`BiomeFogColors`) и наблюдения за реальными тестовыми сессиями с Embeddium/Oculus.
