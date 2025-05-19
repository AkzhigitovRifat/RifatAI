package com.example.myapplication

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: ViewModel by lazy { ViewModel() }

    // Для проверки разрешения на точные будильники
    private val exactAlarmPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Проверяем результат и уведомляем пользователя
        Toast.makeText(this, "Возврат из настроек будильников", Toast.LENGTH_SHORT).show()
    }

    // Для запроса разрешения на уведомления (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Разрешение на уведомления получено", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Без разрешения на уведомления будильники могут не работать", Toast.LENGTH_LONG).show()
        }
    }

    // Переменная для хранения информации о необходимости показать диалог разрешений
    private var needShowPermissionDialog = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Передаем контекст в ViewModel
        viewModel.setAppContext(this)

        // Проверяем разрешения
        checkNotificationPermission()
        checkAlarmPermission()

        // Проверяем, было ли активити запущено из будильника
        val isAlarmTriggered = intent.getBooleanExtra("ALARM_TRIGGERED", false)
        if (isAlarmTriggered) {
            val alarmMessage = intent.getStringExtra("ALARM_MESSAGE") ?: "Время будильника!"
            Toast.makeText(this, alarmMessage, Toast.LENGTH_LONG).show()
        }

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()

            MyApplicationTheme(darkTheme = isDarkTheme) {
                AIAssistantApp(viewModel)

                // Показываем диалог разрешений, если нужно
                if (needShowPermissionDialog) {
                    PermissionDialog(
                        onConfirm = {
                            val intent = Intent().apply {
                                action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                            }
                            exactAlarmPermissionLauncher.launch(intent)
                            needShowPermissionDialog = false
                        },
                        onDismiss = {
                            needShowPermissionDialog = false
                        }
                    )
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        // Проверка разрешений для Android 13+ (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkAlarmPermission() {
        // Проверка разрешения на точные будильники для Android 12+ (S)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // Установим флаг для показа диалога
                needShowPermissionDialog = true
            }
        }
    }
}

@Composable
fun PermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Требуется разрешение") },
        text = {
            Text("Для корректной работы будильников необходимо разрешение на их точную установку")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Перейти в настройки")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

// Модель для представления сообщений в чате
data class ChatMessage(
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val functionCall: String? = null
)

// Вспомогательное расширение для получения Activity из Context
val Context.activity: Activity?
    get() = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity
        else -> null
    }

// Функция для форматирования времени будильника
fun formatAlarmTime(timeInMillis: Long): String {
    val format = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())
    return format.format(Date(timeInMillis))
}

@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAssistantApp(viewModel: ViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val consoleOutput by viewModel.consoleOutput.collectAsState()
    val calendarEvent by viewModel.calendarEvent.collectAsState()
    val clearChatEvent by viewModel.clearChatEvent.collectAsState()
    val alarmEvent by viewModel.alarmEvent.collectAsState() // Добавляем сбор состояния будильника

    // Храним историю сообщений
    val chatHistory = remember { mutableStateListOf<ChatMessage>() }
    var userInput by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Обработка установки будильника
    LaunchedEffect(alarmEvent) {
        alarmEvent?.let { alarm ->
            // Вызываем функцию установки системного будильника
            val success = viewModel.setAlarmInSystemApp(alarm)

            if (success) {
                val formattedTime = formatAlarmTime(alarm.timeInMillis)

                chatHistory.add(ChatMessage(
                    content = "⏰ Будильник установлен на $formattedTime: \"${alarm.message}\"",
                    isFromUser = false,
                    functionCall = "set_alarm(\"$formattedTime\")"
                ))

                // Прокручиваем к последнему сообщению
                coroutineScope.launch {
                    if (chatHistory.size > 0) {
                        lazyListState.animateScrollToItem(chatHistory.size - 1)
                    }
                }
            } else {
                chatHistory.add(ChatMessage(
                    content = "❌ Не удалось установить будильник. Проверьте разрешения приложения.",
                    isFromUser = false
                ))

                // Прокручиваем к последнему сообщению
                coroutineScope.launch {
                    if (chatHistory.size > 0) {
                        lazyListState.animateScrollToItem(chatHistory.size - 1)
                    }
                }
            }
        }
    }

    // Проверяем, запущено ли приложение из будильника
    val isAlarmTriggered = remember {
        context.activity?.intent?.getBooleanExtra("ALARM_TRIGGERED", false) ?: false
    }
    val alarmMessage = remember {
        context.activity?.intent?.getStringExtra("ALARM_MESSAGE") ?: "Время будильника!"
    }

    // Показываем диалог будильника при необходимости
    var showAlarmDialog by remember { mutableStateOf(isAlarmTriggered) }

    if (showAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showAlarmDialog = false },
            title = { Text("⏰ Будильник") },
            text = { Text(alarmMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        showAlarmDialog = false
                        context.activity?.intent?.removeExtra("ALARM_TRIGGERED")
                    }
                ) {
                    Text("Ок")
                }
            }
        )
    }

    LaunchedEffect(uiState, consoleOutput, calendarEvent, clearChatEvent) {
        // Сначала проверяем событие очистки чата
        if (clearChatEvent) {
            chatHistory.clear()
            return@LaunchedEffect // Прерываем обработку, не добавляем новое сообщение
        }

        // Только если чат не был очищен, обрабатываем ответ модели
        when (uiState) {
            is UiState.Success -> {
                val successState = uiState as UiState.Success

                if (successState.modelResponse.isNotEmpty()) {
                    chatHistory.add(ChatMessage(
                        content = successState.modelResponse,
                        isFromUser = false
                    ))
                }

                // Если была вызвана функция, добавляем информацию о вызове
                if (consoleOutput.isNotEmpty()) {
                    chatHistory.add(ChatMessage(
                        content = "Функция вызвана",
                        isFromUser = false,
                        functionCall = "print_to_console(\"$consoleOutput\")"
                    ))
                }

                // Если было событие календаря
                if (calendarEvent != null) {
                    val event = calendarEvent!!
                    chatHistory.add(ChatMessage(
                        content = "Добавление события в календарь",
                        isFromUser = false,
                        functionCall = "add_calendar_event(\"${event.title}\")"
                    ))
                }

                // Прокручиваем к последнему сообщению
                coroutineScope.launch {
                    if (chatHistory.size > 0) {
                        lazyListState.animateScrollToItem(chatHistory.size - 1)
                    }
                }
            }
            is UiState.Error -> {
                val errorState = uiState as UiState.Error
                chatHistory.add(ChatMessage(
                    content = errorState.message,
                    isFromUser = false
                ))

                // Прокручиваем к последнему сообщению
                coroutineScope.launch {
                    lazyListState.animateScrollToItem(chatHistory.size - 1)
                }
            }
            else -> { /* ничего не делаем для других состояний */ }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Верхняя панель
            TopAppBar(
                title = {
                    Text(
                        "AI RIFAT",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Добавляем переключатель темы с текстом вместо иконки
                    TextButton(onClick = { viewModel.toggleDarkTheme() }) {
                        Text(
                            text = if (viewModel.isDarkTheme.value) "☀️" else "🌙",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )


            // Основная часть чата
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                if (chatHistory.isEmpty()) {
                    // Отображаем приветственное сообщение, если чат пустой
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "👋 Привет! Я ваш AI ассистент.",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Задайте мне вопрос или попросите что-нибудь сделать.",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Отображаем историю сообщений
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp),
                        state = lazyListState,
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(chatHistory) { message ->
                            ChatMessageItem(message = message)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Если идет загрузка, показываем индикатор
                        if (uiState is UiState.Loading) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Нижняя панель для ввода сообщений
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp, end = 4.dp),
                        placeholder = { Text("Введите сообщение...") },
                        singleLine = false,
                        maxLines = 3,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            containerColor = Color.Transparent
                        )
                    )

                    IconButton(
                        onClick = {
                            if (userInput.isNotEmpty() && uiState !is UiState.Loading) {
                                // Добавляем сообщение пользователя в историю
                                chatHistory.add(ChatMessage(
                                    content = userInput,
                                    isFromUser = true
                                ))

                                // Отправляем сообщение в ViewModel
                                viewModel.testFunctionCalling(userInput)

                                // Очищаем поле ввода
                                userInput = ""

                                // Прокручиваем к последнему сообщению
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(chatHistory.size - 1)
                                }
                            }
                        },
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        enabled = userInput.isNotEmpty() && uiState !is UiState.Loading
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Отправить",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val alignment = if (message.isFromUser) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isFromUser)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (message.isFromUser)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        // Обычное сообщение
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isFromUser) 16.dp else 4.dp,
                        bottomEnd = if (message.isFromUser) 4.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                color = textColor
            )
        }

        // Если есть вызов функции, показываем его в отдельном пузыре
        message.functionCall?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(8.dp)
            ) {
                Text(
                    text = "🤖 $it",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 12.sp
                )
            }
        }
    }
}
