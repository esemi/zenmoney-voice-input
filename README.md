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

## Установка и запуск на устройстве через adb

`adb` лежит в `$ANDROID_HOME/platform-tools/` — добавь в `PATH` или
вызывай по полному пути.

### 1. Подготовить устройство

- **Реальный телефон**: включи **Developer options** (тапнуть 7 раз по
  Build number в About phone) → включи **USB debugging**. Подключи по
  USB и подтверди RSA-fingerprint на телефоне.
- **Эмулятор**: подними AVD с системным образом, в котором есть Google
  Play Services (иначе не заработает онлайн-распознавание речи).

Проверить, что устройство видно:

```bash
adb devices
# List of devices attached
# R5CRA0XXXXX     device
```

Если устройств больше одного — добавляй `-s <serial>` к каждой команде
`adb`.

### 2. Поставить и запустить debug-сборку

Самый короткий путь — одной gradle-таской:

```bash
./gradlew installDebug
adb shell am start -n dev.esemi.zmvoice.debug/dev.esemi.zmvoice.MainActivity
```

Либо вручную из готового APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` переустанавливает поверх существующей версии без потери данных.
Если меняется подпись (например, ставишь debug поверх release) —
снеси старую сборку: `adb uninstall dev.esemi.zmvoice.debug`.

### 3. Выдать разрешение на микрофон без UI

Удобно для быстрой проверки записи без ручного тапанья по диалогу:

```bash
adb shell pm grant dev.esemi.zmvoice.debug android.permission.RECORD_AUDIO
```

### 4. Снести / переустановить начисто

```bash
adb uninstall dev.esemi.zmvoice.debug
# или сбросить данные, не сноса:
adb shell pm clear dev.esemi.zmvoice.debug
```

`pm clear` чистит DataStore — токены придётся вводить заново.

## Дебаг через adb

### Логи приложения

`Log.d/e/...` из кода летит в `logcat`. Самый простой фильтр —
по PID процесса приложения:

```bash
adb logcat --pid=$(adb shell pidof -s dev.esemi.zmvoice.debug)
```

Если нужен полноценный поток с момента запуска (поймать падение на
старте) — почисть буфер и стартани приложение:

```bash
adb logcat -c
adb shell am start -n dev.esemi.zmvoice.debug/dev.esemi.zmvoice.MainActivity
adb logcat '*:S' AndroidRuntime:E ZmVoice:V SpeechRecognizer:V OkHttp:V
```

Полезные теги в этом проекте:
- `AndroidRuntime` — нативные крэши и необработанные исключения.
- `SpeechRecognizer` — системный движок распознавания (ошибки
  `ERROR_NO_MATCH`, `ERROR_NETWORK` и т.п.).
- `OkHttp` — HTTP-логи Retrofit/OkHttp в debug-сборке (если включён
  `HttpLoggingInterceptor` на уровне `BODY`).

### HTTP-трафик к Claude и ZenMoney

Оба клиента ходят по HTTPS. Чтобы вытащить тело запросов/ответов:
смотри логи `OkHttp` (см. выше) — это проще всего. Если нужен полный
MITM (например, посмотреть заголовки `X-Anthropic-*`), поднимай
**mitmproxy** / **Charles** и направляй устройство через прокси:

```bash
# Поднять mitmproxy на хосте, порт 8080
mitmproxy -p 8080

# Прокинуть TCP-порт хоста на устройство (либо настроить Wi-Fi-proxy в
# системных настройках устройства)
adb reverse tcp:8080 tcp:8080
```

Чтобы Android доверял CA mitmproxy, нужно положить его сертификат в
системный store — на реальном устройстве это требует root, на
эмуляторе делается через `-writable-system`. Для разовой проверки
проще логировать `OkHttp` через `HttpLoggingInterceptor`.

### Подключить отладчик из Android Studio

1. Открыть проект в Android Studio.
2. Run → **Attach Debugger to Android Process** → выбрать
   `dev.esemi.zmvoice.debug`.
3. Точки останова работают только в debug-варианте
   (`isDebuggable = true` ставится автоматически).

Альтернатива из терминала — `adb jdwp` покажет PID процесса, к
которому можно подключиться `jdb`-ом, но это редко кому надо.

### Снять снимок состояния

```bash
# Скриншот в файл
adb exec-out screencap -p > screen.png

# Запись экрана (макс. 3 минуты, MP4)
adb shell screenrecord /sdcard/zmvoice.mp4
# Ctrl+C → выгрузить:
adb pull /sdcard/zmvoice.mp4

# Дамп иерархии view (для разбора кривого UI)
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
```

### Тяжёлая артиллерия: bugreport

Если что-то падает невоспроизводимо — снять полный `bugreport`
(включает logcat, dumpsys, tombstones):

```bash
adb bugreport ./bugreport.zip
```

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

## TODO

- [ ] **Скрыть токены за звёздочками** на экране настроек (Anthropic
      API key, ZenMoney token) — сейчас лежат plain-текстом в
      `OutlinedTextField`. Добавить `visualTransformation =
      PasswordVisualTransformation()` + кнопку «👁» для показа.
- [ ] **Убрать категории доходов** из списка для LLM и из dropdown на
      ConfirmScreen. ZenMoney в `tag.showIncome`/`tag.showOutcome`
      хранит, для какого типа тег применим — фильтровать в
      `ZenRepository.toSnapshot()` или прямо в `ConfirmScreen`/
      `ClaudeClient`. Сейчас при расходе предлагает «Зарплата» и
      прочую дичь.
- [ ] **Подкрасить кнопки на ConfirmScreen** — «Отмена» в нейтральный/
      красноватый, «Записать» в зелёный (или брендовый `primary`).
      Сейчас обе серые `OutlinedButton`/`Button`, легко промахнуться.
- [ ] **Приличный главный экран** — текущий RecordScreen это голая
      кнопка посреди пустого Column. Добавить состояние «последняя
      транзакция», подсказку с примером фразы, индикатор уровня
      сигнала с микрофона (RmsChanged уже приходит, просто не
      используется).
- [ ] **Починить тап на иконку приложения** — после первого запуска
      повторный тап по лаунчер-иконке создаёт новый таск вместо того,
      чтобы вернуть в существующий. Скорее всего нужно
      `android:launchMode="singleTask"` или `singleInstance` вместо
      текущего `singleTop` + проверить `taskAffinity`.
- [ ] **Укоротить системный промпт** для Claude. Сейчас в него летят
      все ~150 тегов плоским списком — это 1-2 тыс. токенов на каждый
      запрос. Варианты: убрать заведомо неподходящие (incomeOnly),
      сократить formatting, использовать prompt caching через
      `cache_control` (тогда снапшот тегов кэшируется на стороне
      Anthropic между вызовами).

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
