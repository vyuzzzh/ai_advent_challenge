- create simple window for ai agent conversation ✅
- ai agent must use yandex gpt api ✅

## Реализовано:

### Структура проекта
1. **Модели данных** (`shared/src/commonMain/kotlin/com/example/ai_window/model/`)
   - `ChatMessage.kt` - модель сообщения чата
   - `YandexGptModels.kt` - модели для работы с Yandex GPT API

2. **Сервисы** (`shared/src/commonMain/kotlin/com/example/ai_window/service/`)
   - `YandexGptService.kt` - клиент для работы с Yandex GPT API

3. **UI слой** (`composeApp/src/commonMain/kotlin/com/example/ai_window/`)
   - `ChatViewModel.kt` - ViewModel для управления состоянием чата
   - `App.kt` - интерфейс чата с Material Design 3

### Функциональность
- ✅ Отправка сообщений к Yandex GPT API
- ✅ Сохранение истории диалога
- ✅ Автоматическая прокрутка к последнему сообщению
- ✅ Индикатор загрузки во время ожидания ответа
- ✅ Обработка и отображение ошибок
- ✅ Кнопка очистки истории чата
- ✅ Адаптивный UI с Material Design 3

### Настройка
1. Получите API ключ и Folder ID от Yandex Cloud
2. Откройте `composeApp/src/commonMain/kotlin/com/example/ai_window/App.kt`
3. Замените `YOUR_YANDEX_API_KEY_HERE` и `YOUR_FOLDER_ID_HERE` на ваши данные
4. Запустите приложение: `./gradlew :composeApp:run`

Подробные инструкции в файле `README_SETUP.md`