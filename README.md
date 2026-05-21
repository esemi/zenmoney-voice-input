# ZenMoney Voice Input

Минимальное Android-приложение: иконка на лаунчере → большая кнопка
«зажми и говори» → распознавание речи системным `SpeechRecognizer` →
LLM-парсинг (Claude Haiku) → подтверждающий экран → запись транзакции
в [ZenMoney](https://zenmoney.ru/) через `/v8/diff`.

## Что умеет

- Записать **расход** или **доход** одной фразой («потратил пятьсот рублей на продукты»).
- Подтянуть из ZenMoney список счетов и категорий и попросить LLM выбрать подходящие.
- Показать распарсенное на экране подтверждения — там же можно поправить сумму, счёт, категорию.
- Записать транзакцию в ZenMoney.

Перевод между счетами и работа с долгами **не поддерживаются** на этой итерации.

## Стек

- Kotlin 2.0 + Jetpack Compose (compose-bom 2024.10)
- Android Gradle Plugin 8.5.2, minSdk 26, targetSdk 34
- Retrofit/OkHttp + kotlinx.serialization
- DataStore Preferences для токенов

## Сборка (debug)

Репозиторий не содержит `gradle-wrapper.jar` (бинарь). Чтобы получить wrapper:

```bash
# Вариант 1: открыть проект в Android Studio — она сама подсосёт wrapper.
# Вариант 2: имея установленный Gradle 8.9+,
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```

Нужен Android SDK с `platform-34` и `build-tools;34.x`. Путь к SDK
задаётся через `local.properties` (`sdk.dir=/path/to/Android/Sdk`) или
переменную окружения `ANDROID_HOME`.

Debug-APK появится в `app/build/outputs/apk/debug/app-debug.apk`,
applicationId — `dev.esemi.zmvoice.debug` (можно ставить рядом с
релизной версией).

## Релизная сборка и публикация в Google Play

Google Play требует **AAB** (Android App Bundle), подписанный твоим
upload-ключом. Сам ключ для подписи установленных APK (app signing key)
Play хранит у себя — при первой загрузке он берёт твой upload-ключ
и связывает с автоматически сгенерированным app signing key (Play App
Signing включён по умолчанию для новых аккаунтов и обязателен).

### 1. Создать upload-keystore

Делается **один раз** и хранится в надёжном месте (потеря ключа = не
сможешь обновлять приложение в Play без процедуры key reset через
поддержку):

```bash
keytool -genkeypair -v \
  -keystore ~/keys/zmvoice-upload.jks \
  -alias zmvoice-upload \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -storetype JKS
```

Google Play требует ключ со сроком как минимум до 22.10.2033 — 10000
дней (≈ 27 лет) с запасом покрывает.

### 2. Прокинуть пароли в Gradle

Создай в корне репо файл `keystore.properties` (он в `.gitignore`):

```properties
storeFile=/home/you/keys/zmvoice-upload.jks
storePassword=...
keyAlias=zmvoice-upload
keyPassword=...
```

`app/build.gradle.kts` уже читает этот файл и, если он есть, подключает
`signingConfig` к release-сборке. Если файла нет — release-таска
соберётся, но **не подпишется** (AAB Play не примет).

Альтернатива для CI: те же поля через переменные окружения
(`ORG_GRADLE_PROJECT_*`) или через `-PstoreFile=…` при вызове Gradle —
тогда `keystore.properties` не нужен.

### 3. Поднять versionCode перед каждой публикацией

В `app/build.gradle.kts`:

```kotlin
defaultConfig {
    versionCode = 2          // монотонно растущее целое
    versionName = "0.2.0"    // человекочитаемая версия
}
```

`versionCode` обязан расти от релиза к релизу, иначе Play отклонит
загрузку.

### 4. Собрать release AAB

```bash
./gradlew clean bundleRelease
```

Артефакт: `app/build/outputs/bundle/release/app-release.aab`.

Параллельно можно собрать релизный APK для локального теста:

```bash
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

В release-сборке включены `minify` и `shrinkResources` — если что-то
сломается из-за R8, смотри `proguard-rules.pro` (там уже стоят правила
для kotlinx.serialization). Логи R8 — в
`app/build/outputs/mapping/release/`.

### 5. Локально проверить подпись

```bash
# Проверить, что AAB подписан и pertinent meta-data корректны
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose \
  app/build/outputs/apk/release/app-release.apk

# Имя и хэш upload-ключа (Play потом покажет тот же fingerprint)
keytool -list -v -keystore ~/keys/zmvoice-upload.jks -alias zmvoice-upload
```

Сертификат AAB напрямую `apksigner` не валидирует — для смока ставь
именно `app-release.apk` через `adb install`.

### 6. Подготовить листинг в Google Play Console

Один раз на новый проект:

1. Зайти в [Play Console](https://play.google.com/console/) → **Create app**.
2. Заполнить App access (микрофон → объяснить, что речь не покидает
   устройство, кроме отправки в Anthropic и ZenMoney), Ads (нет),
   Content rating, Target audience, News app (нет).
3. **App content → Data safety**: задекларировать передачу
   - аудио/текста речи в Anthropic API,
   - финансовых данных в ZenMoney API.
   Иначе Play зарубит на ревью.
4. **Privacy policy URL** — обязателен (есть permission `RECORD_AUDIO`
   + сетевые вызовы во внешние сервисы). Подойдёт страница на
   GitHub Pages.
5. **Store listing**: иконка 512×512, скриншоты (минимум 2 для phone,
   1024×500 feature graphic), описание.

### 7. Загрузить AAB

В Play Console → **Production** (или **Internal testing** на первый
прогон) → **Create new release** → загрузить
`app-release.aab` → заполнить release notes → **Review release** →
**Start rollout**.

Первый релиз выкатывай через **Internal testing** — там нет ревью
дольше пары часов, можно гонять обновления быстро. Production-ревью
обычно 1–7 дней.

### 8. Обновления

Дальше цикл: поднял `versionCode`/`versionName` → `./gradlew bundleRelease` →
загрузил AAB в Play Console → выкатил. **Тот же upload-ключ
используется для всех будущих релизов** — потерял = всё, новые
версии только через процедуру замены ключа через поддержку Google.

## Первый запуск

1. Установить APK на устройство/эмулятор с Android 8+ (Google services
   нужны для онлайн-распознавания речи; на «голом» AOSP может
   потребоваться `Google` Speech Services).
2. Открыть приложение → выдать permission на микрофон.
3. Тапнуть **«Настройки»** в правом верхнем углу:
   - Вставить **ZenMoney token**. Получить можно через OAuth-консоль
     ZenMoney или через сторонние скрипты (см. документацию API).
   - Вставить **Anthropic API key** (создать на console.anthropic.com).
   - Нажать **«Подтянуть из ZenMoney»** → выбрать счёт по умолчанию
     из выпадающего списка.
   - **«Сохранить»**.
4. Вернуться, зажать большую кнопку, произнести фразу, отпустить.
5. Проверить разбор на экране подтверждения и нажать **«Записать»**.

## Безопасность токенов

Токены лежат в обычном `DataStore Preferences` без шифрования. Это
ок для личного устройства, но **не** клади приложение на чужой телефон.
Если нужно — оберни в `EncryptedSharedPreferences` / Android Keystore.

## Известные ограничения

- Используется системный `SpeechRecognizer` — на устройствах без Google
  Speech Services распознавание может не работать. Включи нужный engine
  в системных настройках (Settings → System → Languages → Speech).
- Категории/счета кэшируются после первого `«Подтянуть из ZenMoney»` и
  не обновляются автоматически. Если завёл новую категорию — снова
  нажми «Подтянуть».
- Нет тестов и нет CI — это игрушка-MVP.

## Что внутри

```
app/src/main/kotlin/dev/esemi/zmvoice/
├── AppContainer.kt          # ручное DI
├── MainActivity.kt          # entry point
├── VoiceViewModel.kt        # стейт-машина Idle→Listening→Parsing→Confirm→Sending→Done
├── ZmVoiceApp.kt            # Application с контейнером
├── data/SettingsStore.kt    # DataStore
├── llm/                     # Anthropic Messages API + tool use
├── speech/                  # обёртка SpeechRecognizer → Flow
├── ui/                      # Compose-экраны
└── zenmoney/                # /v8/diff клиент + репозиторий
```
