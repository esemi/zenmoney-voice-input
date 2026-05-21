# ZenMoney Voice Input

Android-приложение: зажал кнопку → сказал фразу про трату →
[ZenMoney](https://zenmoney.ru/) получил транзакцию. Распознавание
речи — системное, разбор фразы — Claude Haiku, запись — через
`/v8/diff`.

## Что умеет

- Записать **трату** одной фразой («потратил пятьсот на продукты»).
- Сам выбирает счёт и категорию из подтянутых из ZenMoney списков.
- Экран подтверждения: правка суммы, валюты, счёта, категории.
- К комментарию транзакции автоматически дописывается `from zmvoice`.

Не умеет: доходы, переводы между счетами, долги.

## Стек

Kotlin 2.0 · Jetpack Compose (BOM 2024.10) · AGP 8.5.2 · minSdk 26 ·
targetSdk 34 · OkHttp + kotlinx.serialization · DataStore.

## Сборка

```bash
# Если нет gradle-wrapper.jar — один раз:
gradle wrapper --gradle-version 8.9
# Или просто открой проект в Android Studio.

./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Нужен Android SDK с `platform-34` и `build-tools;34.x`. Путь к SDK —
через `local.properties` (`sdk.dir=...`) или переменную окружения
`ANDROID_HOME`.

Идентификатор отладочного варианта — `dev.esemi.zmvoice.debug`,
ставится рядом с релизной версией.

## Установка на устройство

```bash
adb devices                          # устройство должно быть видно
./gradlew installDebug
adb shell am start -n dev.esemi.zmvoice.debug/dev.esemi.zmvoice.MainActivity
adb shell pm grant dev.esemi.zmvoice.debug android.permission.RECORD_AUDIO
```

Снести / сбросить данные:

```bash
adb uninstall dev.esemi.zmvoice.debug
adb shell pm clear dev.esemi.zmvoice.debug   # обнуляет токены
```

На реальном телефоне нужен режим разработчика и отладка по USB. На
эмуляторе — образ с сервисами Google, иначе распознавать речь нечем.

## Логи

Свои сообщения пишутся под тегами `ZmVoice` (распознанный текст и
варианты) и `ZmVoice.Claude` (системный промпт и сырой ответ):

```bash
adb logcat '*:S' ZmVoice:V ZmVoice.Claude:V AndroidRuntime:E
```

## Первый запуск

1. Поставить отладочную сборку, выдать доступ к микрофону.
2. **Настройки** в правом верхнем углу:
   - вставить **токен ZenMoney**;
   - вставить **ключ Anthropic** ([console.anthropic.com](https://console.anthropic.com));
   - **«Подтянуть из ZenMoney»** → выбрать счёт по умолчанию;
   - **«Сохранить»**.
3. Зажать большую кнопку, произнести фразу, отпустить.
4. Проверить разбор на экране подтверждения → **«Записать»**.

## Безопасность

Токены шифруются AES-256/GCM перед записью в `DataStore`. Ключ
шифрования лежит в Android Keystore (алиас `zmvoice_master`), достать
его без root и unlocked-устройства нельзя.

## Известные ограничения

- Без сервисов Google распознавать речь нечем (Настройки → Система →
  Языки → Голосовой ввод).
- Снимок счетов и категорий из ZenMoney кэшируется и сам не
  обновляется — после новой категории нажми «Подтянуть».
- Тестов и сборки на сервере нет — это игрушка.

## TODO

- [ ] **Скрыть токены за звёздочками** в настройках — сейчас видны
      открытым текстом.
- [ ] **Убрать категории доходов** из списка для модели и выпадашки —
      фильтровать по `showOutcome` в `ZenRepository.toSnapshot()`.
- [ ] **Подкрасить кнопки на экране подтверждения** — «Отмена»
      нейтральной, «Записать» заметной (зелёная/брендовая).
- [ ] **Приличный главный экран** — пример фразы, индикатор громкости
      (`onRmsChanged` уже приходит), последняя транзакция.
- [ ] **Тап по иконке** создаёт новую задачу вместо возврата в
      существующую. Проверить `launchMode` (`singleTask` вместо
      `singleTop`) и `taskAffinity`.
- [ ] **Укоротить системный промпт** — все ~150 категорий летят
      плоским списком на каждый запрос. Заюзать `cache_control`
      Anthropic, чтобы снапшот кэшировался между вызовами.
- [ ] **Починить отправку транзакции в ZenMoney.** `POST /v8/diff` из
      `ZenRepository.submit()` не записывает — проверить ответ
      (логи `OkHttp`), поля DTO, актуальный `serverTimestamp` (сейчас
      всегда `0`).

## Что внутри

```
app/src/main/kotlin/dev/esemi/zmvoice/
├── MainActivity.kt          # точка входа
├── ZmVoiceApp.kt            # Application + ручная сборка зависимостей
├── VoiceViewModel.kt        # стейт-машина Idle→Listening→Parsing→Confirm→Sending→Done
├── speech/                  # SpeechRecognizer → Flow, варианты распознавания
├── llm/                     # Claude Messages API + tool use
├── zenmoney/                # клиент /v8/diff + репозиторий
├── data/SettingsStore.kt    # DataStore
└── ui/                      # экраны на Compose (запись, подтверждение, настройки)
```
