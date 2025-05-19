package com.example.myapplication
import android.provider.AlarmClock
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ViewModel : ViewModel() {
    private val apiKey = apiKey
    private val model1 = "anthropic/claude-3.7-sonnet"
    private val model2 = "Mistral7B"
    // Событие добавления будильника

    private val _alarmEvent = MutableStateFlow<AlarmInfo?>(null)
    val alarmEvent: StateFlow<AlarmInfo?> = _alarmEvent.asStateFlow()

    // Модель для хранения информации о будильнике
    data class AlarmInfo(
        val timeInMillis: Long,
        val message: String = "Будильник"
    )

    private val systemMessage = "Ты дружелюбный AI ассистент, твой создатель Рифат. Твоя задача - быть полезным, " +
            "информативным и приятным в общении. Используй дружелюбный тон и отвечай " +
            "на вопросы максимально понятно. Если пользователь спросит, кто тебя создал, " +
            "с гордостью скажи, что твой создатель - Рифат. " +
            "Так же, ты являешься языковой моделью Mistral 7B " +
            "Если пользователь попросит включить или выключить тёмную тему, выполни эту команду через функцию. " +
            "Если пользователь попросит очистить чат или историю сообщений, используй функцию clear_chat()."

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _consoleOutput = MutableStateFlow("")
    val consoleOutput: StateFlow<String> = _consoleOutput.asStateFlow()

    private val _calendarEvent = MutableStateFlow<CalendarEvent?>(null)
    val calendarEvent: StateFlow<CalendarEvent?> = _calendarEvent.asStateFlow()

    // Событие очистки чата
    private val _clearChatEvent = MutableStateFlow(false)
    val clearChatEvent: StateFlow<Boolean> = _clearChatEvent.asStateFlow()

    // Тёмная тема
    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    // Храним контекст для доступа к ContentResolver
    private var appContext: Context? = null

    private val server: Server

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://openrouter.ai/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        server = retrofit.create(Server::class.java)
    }

    // Устанавливаем контекст приложения
    fun setAppContext(context: Context) {
        appContext = context.applicationContext
    }

    // Функция переключения темы
    fun toggleDarkTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
        Log.d("Theme", "Тема переключена на: " + if (_isDarkTheme.value) "тёмную" else "светлую")
    }

    fun testFunctionCalling(userInput: String) {
        _uiState.value = UiState.Loading
        _consoleOutput.value = ""
        _calendarEvent.value = null

// tool, prompt
        //change theme

        // 1. Функция для вывода в консоль
        val printTool = MCPTool(
            type = "function",
            function = MCPFunction(
                name = "print_to_console",
                description = "Prints text to the console",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "message" to mapOf(
                            "type" to "string",
                            "description" to "The text to print to the console"
                        )
                    ),
                    "required" to listOf("message")
                )
            )
        )

        val addCalendarEventTool = MCPTool(
            type = "function",
            function = MCPFunction(
                name = "add_calendar_event",
                description = "Adds an event to the user's calendar",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "title" to mapOf(
                            "type" to "string",
                            "description" to "Title or name of the event"
                        ),
                        "date" to mapOf(
                            "type" to "string",
                            "description" to "Date of the event in format YYYY-MM-DD"
                        ),
                        "time" to mapOf(
                            "type" to "string",
                            "description" to "Time of the event in 24-hour format HH:MM"
                        ),
                        "duration" to mapOf(
                            "type" to "integer",
                            "description" to "Duration of the event in minutes, default is 60",
                            "default" to 60
                        ),
                        "description" to mapOf(
                            "type" to "string",
                            "description" to "Description or additional details of the event"
                        )
                    ),
                    "required" to listOf("title", "date", "time")
                )
            )
        )

        // 3. Функция для переключения темы
        val toggleThemeTool = MCPTool(
            type = "function",
            function = MCPFunction(
                name = "toggle_theme",
                description = "Toggles between light and dark theme",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "mode" to mapOf(
                            "type" to "string",
                            "description" to "The theme mode to set: 'dark', 'light', or 'toggle'",
                            "enum" to listOf("dark", "light", "toggle")
                        )
                    ),
                    "required" to listOf("mode")
                )
            )
        )

        val clearChatTool = MCPTool(
            type = "function",
            function = MCPFunction(
                name = "clear_chat",
                description = "Clears all messages from the chat history",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "confirm" to mapOf(
                            "type" to "boolean",
                            "description" to "Confirmation to clear the chat",
                            "default" to true
                        )
                    ),
                    "required" to listOf<String>() // Пустой список с явным указанием типа
                )
            )
        )

// 5. Функция для добавления будильника
        val setAlarmTool = MCPTool(
            type = "function",
            function = MCPFunction(
                name = "set_alarm",
                description = "Sets an alarm for a specific time and date",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "time" to mapOf(
                            "type" to "string",
                            "description" to "Time for the alarm in format HH:MM (24-hour format)"
                        ),
                        "date" to mapOf(
                            "type" to "string",
                            "description" to "Date for the alarm. Can be 'tomorrow', 'завтра', 'day after tomorrow', 'послезавтра', or a specific date in format YYYY-MM-DD. If not provided, alarm will be set for today."
                        ),
                        "message" to mapOf(
                            "type" to "string",
                            "description" to "Optional message or label for the alarm",
                            "default" to "Будильник"
                        )
                    ),
                    "required" to listOf("time")
                )
            )
        )


        val messagesList = mutableListOf(
            MCPMessage(role = "system", content = systemMessage),
            MCPMessage(role = "user", content = userInput)
        )

        // Формируем запрос для Docker с функциями
        val request = DockerRequest(
            model = model2,
            messages = messagesList,
            tools = listOf(printTool, addCalendarEventTool, toggleThemeTool, clearChatTool, setAlarmTool),
            max_tokens = 1024,
            temperature = 0.7f
        )

        // Подготавливаем заголовок авторизации
        val authHeader = "Bearer $apiKey"

        // Отправляем запрос в корутине
        viewModelScope.launch {
            try {
                //Log.d("OpenRouter", "Отправляем запрос с API ключом: ${apiKey.take(5)}...")
                val response = server.generateCompletion(authHeader, request)

                var modelResponse = ""
                var functionOutput = ""
                var eventDetails = ""
                var themeDetails = ""
                var clearChatDetails = ""

                // Обрабатываем ответ
                if (response.choices.isNotEmpty()) {
                    val choice = response.choices[0]
                    val message = choice.message

                    if (message.content != null) {
                        modelResponse = message.content
                    }

                    if (!message.tool_calls.isNullOrEmpty()) {
                        for (toolCall in message.tool_calls) {
                            when (toolCall.function.name) {
                                "print_to_console" -> {
                                    try {
                                        val gson = Gson()
                                        val argsMap = gson.fromJson(toolCall.function.arguments, Map::class.java)
                                        val messageText = argsMap["message"] as String

                                        functionOutput = messageText
                                        _consoleOutput.value = messageText

                                        // Выводим в консоль
                                        println(messageText)
                                    } catch (e: Exception) {
                                    }
                                }
                                "add_calendar_event" -> {
                                    try {
                                        val gson = Gson()
                                        val argsMap = gson.fromJson(toolCall.function.arguments, JsonObject::class.java)

                                        val title = argsMap.get("title").asString
                                        val date = argsMap.get("date").asString
                                        val time = argsMap.get("time").asString
                                        val duration = if (argsMap.has("duration")) argsMap.get("duration").asInt else 60
                                        val description = if (argsMap.has("description")) argsMap.get("description").asString else ""


                                        // Парсим дату и время
                                        val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                        val startDateTime = dateTimeFormat.parse("$date $time")?.time ?: System.currentTimeMillis()
                                        val endDateTime = startDateTime + (duration * 60 * 1000)

                                        // Создаем объект события календаря (только для отображения)
                                        val event = CalendarEvent(
                                            title = title,
                                            startTime = startDateTime,
                                            endTime = endDateTime,
                                            description = description
                                        )

                                        // Сохраняем событие в StateFlow для отображения
                                        _calendarEvent.value = event

                                        // Заглушка: только показываем информацию без добавления в календарь
                                        val resultMsg = "📅 Событие: \"$title\" на $date, $time (длительность: $duration мин)" +
                                                if (description.isNotEmpty()) "\nОписание: $description" else ""

                                        eventDetails = resultMsg

                                    } catch (e: Exception) {
                                        eventDetails = "Ошибка при создании события: ${e.message}"
                                    }
                                }
                                "toggle_theme" -> {
                                    try {
                                        val gson = Gson()
                                        val argsMap = gson.fromJson(toolCall.function.arguments, Map::class.java)
                                        val mode = argsMap["mode"] as String

                                        when (mode) {
                                            "dark" -> _isDarkTheme.value = true
                                            "light" -> _isDarkTheme.value = false
                                            "toggle" -> _isDarkTheme.value = !_isDarkTheme.value
                                        }

                                        themeDetails = "🎨 Тема изменена на: ${if (_isDarkTheme.value) "тёмную" else "светлую"}"
                                    } catch (e: Exception) {
                                    }
                                }
                                "clear_chat" -> {
                                    try {
                                        val gson = Gson()
                                        val argsMap = gson.fromJson(toolCall.function.arguments, Map::class.java)
                                        val confirm = argsMap["confirm"] as? Boolean ?: true

                                        if (confirm) {
                                            // Создаем событие очистки чата
                                            _clearChatEvent.value = true

                                            // Устанавливаем пустой ответ модели, чтобы не показывать сообщение
                                            modelResponse = ""

                                            // Сбрасываем флаг после небольшой задержки
                                            viewModelScope.launch {
                                                kotlinx.coroutines.delay(100)
                                                _clearChatEvent.value = false
                                            }

                                            clearChatDetails = "🧹 Чат очищен"
                                        }
                                    } catch (e: Exception) {
                                    }
                                }
                                // Предполагается, что это часть метода handleFunctionCall или похожего метода в ViewModel
                                "set_alarm" -> {
                                    try {
                                        val gson = Gson()
                                        val argsMap = gson.fromJson(toolCall.function.arguments, JsonObject::class.java)

                                        // Получаем время в формате HH:MM
                                        val timeString = argsMap.get("time").asString

                                        // Получаем дату (опционально)
                                        val dateString = if (argsMap.has("date")) argsMap.get("date").asString else ""

                                        // Получаем сообщение (опционально)
                                        val message = if (argsMap.has("message")) argsMap.get("message").asString else "Будильник"

                                        // Парсим время и дату
                                        val alarmTimeMillis = calculateAlarmTime(timeString, dateString)

                                        // Создаем информацию о будильнике
                                        val alarmInfo = AlarmInfo(alarmTimeMillis, message)

                                        // Сохраняем в LiveData/StateFlow для обработки в MainActivity
                                        _alarmEvent.value = alarmInfo

                                        // Форматируем время для ответа
                                        val formattedTime = formatAlarmDateTime(alarmTimeMillis)

                                        // Возвращаем результат в формате, подходящем для вашего метода
                                        "${toolCall.id}#Будильник установлен на $formattedTime: \"$message\""
                                    } catch (e: Exception) {
                                        Log.e("ViewModel", "Ошибка при обработке установки будильника: ${e.message}", e)
                                        "${toolCall.id}#Ошибка при установке будильника: ${e.message}"
                                    }
                                }

                            }
                        }
                    }
                }

                // Собираем информацию о функциях
                val functionDetails = mutableListOf<String>()
                if (functionOutput.isNotEmpty()) {
                    functionDetails.add("Функция print_to_console вызвана: \"$functionOutput\"")
                }
                if (eventDetails.isNotEmpty()) {
                    functionDetails.add(eventDetails)
                }
                if (themeDetails.isNotEmpty()) {
                    functionDetails.add(themeDetails)
                }
                if (clearChatDetails.isNotEmpty()) {
                    functionDetails.add(clearChatDetails)
                }

                _uiState.value = UiState.Success(
                    modelResponse = modelResponse,
                    functionCallDetails = if (functionDetails.isNotEmpty()) functionDetails.joinToString("\n") else "Функции не были вызваны"
                )

            } catch (e: Exception) {
                if (e is retrofit2.HttpException) {
                    //Log.e("OpenRouter", "HTTP код: ${e.code()}")
                    //Log.e("OpenRouter", "HTTP сообщение: ${e.message()}")
                    try {
                        //Log.e("OpenRouter", "Тело ответа: ${e.response()?.errorBody()?.string()}")
                    } catch (e2: Exception) {
                        //Log.e("OpenRouter", "Не удалось прочитать тело ответа", e2)
                    }
                }
                _uiState.value = UiState.Error("Ошибка: ${e.message}")
            }
        }
    }

    // Устанавливает будильник через AlarmManager
    // Устанавливает будильник через AlarmManager
    fun setAlarmInSystemApp(alarmInfo: AlarmInfo): Boolean {
        return try {
            appContext?.let { context ->
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = alarmInfo.timeInMillis
                }
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)

                // Добавляем информацию о дате в интент
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, alarmInfo.message)

                    // Устанавливаем дату будильника (дни недели)
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    val daysOfWeekList = ArrayList<Int>().apply { add(dayOfWeek - 1) } // Конвертируем в формат 0-6
                    putExtra(AlarmClock.EXTRA_DAYS, daysOfWeekList)

                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                Log.d("Alarm", "Установка будильника: ${formatAlarmDateTime(alarmInfo.timeInMillis)}")

                try {
                    context.startActivity(intent)
                    Log.d("Alarm", "Интент запущен успешно")
                    true
                } catch (e: Exception) {
                    Log.e("Alarm", "Ошибка при запуске интента: ${e.message}")
                    // Запасной вариант
                    setAlarm(alarmInfo)
                }
            } ?: false
        } catch (e: Exception) {
            Log.e("Alarm", "Общая ошибка при установке будильника: ${e.message}")
            setAlarm(alarmInfo)
        }
    }

    // Вспомогательная функция для проверки, установлено ли приложение
    private fun isPackageInstalled(packageName: String, packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun setAlarm(alarmInfo: AlarmInfo): Boolean {
        return try {
            appContext?.let { context ->
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("ALARM_MESSAGE", alarmInfo.message)
                    putExtra("ALARM_ID", alarmInfo.timeInMillis.toInt())
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    alarmInfo.timeInMillis.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Проверяем разрешения для Android 12+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Проверяем, есть ли разрешение на установку точных будильников
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            alarmInfo.timeInMillis,
                            pendingIntent
                        )
                    } else {
                        // Если разрешения нет, нужно направить пользователя в настройки
                        // для получения разрешения
                        showAlarmPermissionDialog(context)
                        Log.e("Alarm", "Нет разрешения на установку точных будильников")
                        return false
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Для Android 6-11
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        alarmInfo.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // Для старых версий Android
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        alarmInfo.timeInMillis,
                        pendingIntent
                    )
                }

                Log.d("Alarm", "Будильник установлен на ${formatAlarmDateTime(alarmInfo.timeInMillis)}")
                true
            } ?: false
        } catch (e: SecurityException) {
            // Обрабатываем исключения безопасности (отсутствие разрешения)
            Log.e("Alarm", "Ошибка безопасности при установке будильника: ${e.message}", e)
            appContext?.let { showAlarmPermissionDialog(it) }
            false
        } catch (e: Exception) {
            Log.e("Alarm", "Ошибка при установке будильника: ${e.message}", e)
            false
        }
    }

    // Метод для отображения диалога о необходимости разрешения
    private fun showAlarmPermissionDialog(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Создаем Intent для перехода в настройки разрешений будильника
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            }

            // Запускаем Intent с флагом NEW_TASK, так как у нас может не быть активности
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            // Также можно показать Toast-уведомление
            android.widget.Toast.makeText(
                context,
                "Требуется разрешение для установки будильников",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }


    // Рассчитывает время будильника в миллисекундах
    fun calculateAlarmTime(timeString: String, dateString: String?): Long {
        val calendar = Calendar.getInstance()

        // Парсинг времени (часы:минуты)
        val timeParts = timeString.split(":")
        val hour = timeParts[0].trim().toInt()
        val minute = timeParts[1].trim().toInt()

        // Устанавливаем часы и минуты
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Обработка даты
        if (!dateString.isNullOrEmpty()) {
            when {
                // Обработка относительных дат
                dateString.equals("tomorrow", ignoreCase = true) ||
                        dateString.equals("завтра", ignoreCase = true) -> {
                    // Добавляем 1 день к текущей дате
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    Log.d("Alarm", "Установка будильника на завтра (${calendar.time})")
                }

                dateString.equals("day after tomorrow", ignoreCase = true) ||
                        dateString.equals("послезавтра", ignoreCase = true) -> {
                    // Добавляем 2 дня к текущей дате
                    calendar.add(Calendar.DAY_OF_YEAR, 2)
                    Log.d("Alarm", "Установка будильника на послезавтра (${calendar.time})")
                }

                // Обработка конкретной даты (например, "2023-03-26")
                else -> {
                    try {
                        // Пытаемся парсить как конкретную дату в формате YYYY-MM-DD
                        val dateParts = dateString.split("-")
                        if (dateParts.size == 3) {
                            val year = dateParts[0].toInt()
                            val month = dateParts[1].toInt() - 1 // Calendar месяцы начинаются с 0
                            val day = dateParts[2].toInt()

                            calendar.set(Calendar.YEAR, year)
                            calendar.set(Calendar.MONTH, month)
                            calendar.set(Calendar.DAY_OF_MONTH, day)

                        }
                    } catch (e: Exception) {
                        // Если не удалось распарсить, оставляем текущую дату
                    }
                }
            }
        }

        // Если установленное время уже прошло, добавляем 1 день
        val currentTime = System.currentTimeMillis()
        if (calendar.timeInMillis < currentTime) {
            Log.d("Alarm", "Установленное время уже прошло, добавляем 1 день")
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return calendar.timeInMillis
    }

    fun formatAlarmDateTime(timeInMillis: Long): String {
        val calendar = Calendar.getInstance()
        val currentDate = Calendar.getInstance()
        calendar.timeInMillis = timeInMillis

        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val date = dateFormat.format(calendar.time)
        val time = timeFormat.format(calendar.time)

        // Проверяем, сегодня ли это, завтра или другой день
        return when {
            // Сегодня
            calendar.get(Calendar.YEAR) == currentDate.get(Calendar.YEAR) &&
                    calendar.get(Calendar.DAY_OF_YEAR) == currentDate.get(Calendar.DAY_OF_YEAR) -> {
                "сегодня, $time"
            }
            // Завтра
            calendar.get(Calendar.YEAR) == currentDate.get(Calendar.YEAR) &&
                    calendar.get(Calendar.DAY_OF_YEAR) == currentDate.get(Calendar.DAY_OF_YEAR) + 1 -> {
                "завтра, $time"
            }
            // Послезавтра
            calendar.get(Calendar.YEAR) == currentDate.get(Calendar.YEAR) &&
                    calendar.get(Calendar.DAY_OF_YEAR) == currentDate.get(Calendar.DAY_OF_YEAR) + 2 -> {
                "послезавтра, $time"
            }
            // Другие дни
            else -> {
                "$date, $time"
            }
        }
    }




    private fun addEventToCalendar(context: Context, event: CalendarEvent): Boolean {
        // Заглушка: просто логируем событие, не добавляем в календарь
        Log.d("Calendar", "Симуляция добавления события: ${event.title}")
        return true
    }


    fun copyToClipboard(text: String): Boolean {
        return try {
            appContext?.let { context ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("AI Response", text)
                clipboard.setPrimaryClip(clip)
                true
            } ?: false
        } catch (e: Exception) {
            Log.e("Clipboard", "Ошибка при копировании текста: ${e.message}", e)
            false
        }
    }
}
