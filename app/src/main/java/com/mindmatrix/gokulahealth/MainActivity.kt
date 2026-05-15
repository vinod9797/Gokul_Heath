package com.mindmatrix.gokulahealth

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

private val Accent = androidx.compose.ui.graphics.Color(0xFF3F6F5A)
private val Ink = androidx.compose.ui.graphics.Color(0xFF202124)
private val Muted = androidx.compose.ui.graphics.Color(0xFF6B7280)
private val Line = androidx.compose.ui.graphics.Color(0xFFE5E7EB)
private val Page = androidx.compose.ui.graphics.Color(0xFFF5F3EE)
private val CardSurface = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
private val SelectedSurface = androidx.compose.ui.graphics.Color(0xFFE8EEE9)

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: GokulaViewModel

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) viewModel.updateSymptomText(spoken)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderReceiver.createChannel(this)
        requestRuntimePermissions()
        viewModel = ViewModelProvider(this, GokulaViewModel.Factory(applicationContext))[GokulaViewModel::class.java]
        setContent {
            GokulaApp(
                vm = viewModel,
                onVoiceInput = { startVoiceInput() },
                onSharePassport = { cow -> sharePassport(cow) }
            )
        }
    }

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak cattle symptoms")
        }
        voiceLauncher.launch(intent)
    }

    private fun sharePassport(cow: Cattle) {
        viewModelScopeSafe {
            val file = viewModel.createPassportPdf(this@MainActivity, cow)
            val uri = FileProvider.getUriForFile(this@MainActivity, "${packageName}.files", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(sendIntent, "Share cattle passport"))
        }
    }

    private fun viewModelScopeSafe(block: suspend () -> Unit) {
        viewModel.viewModelScope.launch {
            runCatching { block() }.onFailure {
                Toast.makeText(this@MainActivity, it.message ?: "Unable to share", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

data class AppUiState(
    val splashDone: Boolean = false,
    val loggedIn: Boolean = false,
    val profile: FarmerProfile? = null,
    val cattle: List<Cattle> = emptyList(),
    val reminders: List<Vaccination> = emptyList(),
    val milkEntries: List<MilkEntry> = emptyList(),
    val selectedCowId: Long = 0,
    val screen: Screen = Screen.Home,
    val aiMessages: List<Pair<String, String>> = listOf("AI Vet" to "Type symptoms or use voice input. I can explain likely causes, first aid, and urgent warning signs."),
    val aiAlerts: List<String> = emptyList(),
    val search: String = "",
    val sevenDayAverage: Double = 0.0,
    val monthlyAverage: Double = 0.0,
    val todayMilk: Double = 0.0,
    val pregnantCount: Int = 0,
    val upcomingCount: Int = 0
)

enum class Screen(val label: String) {
    Home("Home"), Cattle("Cattle"), Milk("Milk"), Reminders("Reminders"), Breeding("Breeding"), AI("AI"), Analytics("Analytics"), Profile("Profile")
}

class GokulaViewModel(private val context: Context) : ViewModel() {
    private val db = AppDatabase.get(context)
    private val repository = GokulaRepository(db)
    private val ai = LocalVeterinaryAiClient()
    private val cloudSync: CloudSyncService = FirebaseCloudSyncService()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    var state by mutableStateOf(AppUiState())
        private set
    var symptomText by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            kotlinx.coroutines.delay(900)
            state = state.copy(splashDone = true)
            load()
        }
    }

    fun updateSymptomText(value: String) {
        symptomText = value
    }

    fun setScreen(screen: Screen) {
        state = state.copy(screen = screen)
    }

    fun logout() {
        state = state.copy(loggedIn = false, screen = Screen.Home)
    }

    fun login(mobile: String, otp: String, name: String, village: String, language: String) {
        if (mobile.length < 10 || otp.length < 4 || name.isBlank()) return
        viewModelScope.launch {
            val profile = FarmerProfile().apply {
                this.mobile = mobile
                this.name = name
                this.village = village
                this.language = language
                this.cattleCount = state.cattle.size
            }
            repository.saveProfile(profile)
            cloudSync.enqueueProfile(profile)
            state = state.copy(loggedIn = true, profile = profile)
            load()
        }
    }

    fun saveCow(form: CattleForm) {
        viewModelScope.launch {
            val cow = Cattle().apply {
                name = form.name.ifBlank { "Cow ${state.cattle.size + 1}" }
                earTag = form.earTag
                breed = form.breed
                age = form.age.toIntOrNull() ?: 0
                weight = form.weight.toDoubleOrNull() ?: 0.0
                gender = form.gender
                photoUri = form.photoUri
                purchaseDate = parseDate(form.purchaseDate)
                pregnant = form.pregnant
                lastHeatDate = parseDate(form.lastHeatDate)
                breedingStatus = form.breedingStatus
                createdAt = System.currentTimeMillis()
            }
            repository.insertCow(cow)
            load()
        }
    }

    fun deleteCow(cow: Cattle) {
        viewModelScope.launch {
            repository.deleteCow(cow)
            load()
        }
    }

    fun updateSearch(query: String) {
        state = state.copy(search = query)
        viewModelScope.launch {
            val rows = repository.searchCattle(query)
            state = state.copy(cattle = rows)
        }
    }

    fun selectCow(id: Long) {
        state = state.copy(selectedCowId = id)
        loadAnalytics(id)
    }

    fun saveMilk(morning: String, evening: String, notes: String) {
        val cow = selectedCow() ?: return
        val morningValue = morning.toDoubleOrNull() ?: 0.0
        val eveningValue = evening.toDoubleOrNull() ?: 0.0
        viewModelScope.launch {
            val entry = MilkEntry().apply {
                cattleId = cow.id
                morningLiters = morningValue
                eveningLiters = eveningValue
                totalLiters = morningValue + eveningValue
                this.notes = notes
                entryDate = startOfToday()
            }
            repository.insertMilk(entry)
            maybeNotifyMilkDrop(cow, entry.totalLiters)
            load()
        }
    }

    fun saveReminder(vaccine: String, dueDate: String, repeatDays: String) {
        val cow = selectedCow() ?: return
        viewModelScope.launch {
            val reminder = Vaccination().apply {
                cattleId = cow.id
                vaccineName = vaccine.ifBlank { "General shot" }
                this.dueDate = parseDate(dueDate)
                this.repeatDays = repeatDays.toIntOrNull() ?: 0
                completed = false
                reminderSet = true
            }
            val id = repository.insertReminder(reminder)
            scheduleReminder(id.toInt(), cow.name, reminder.vaccineName, reminder.dueDate)
            load()
        }
    }

    fun markCompleted(reminder: Vaccination) {
        viewModelScope.launch {
            reminder.completed = true
            repository.updateReminder(reminder)
            load()
        }
    }

    fun askAi() {
        val cow = selectedCow()
        val prompt = symptomText.trim()
        if (prompt.isEmpty()) return
        viewModelScope.launch {
            val answer = ai.answer(prompt, state.profile?.language ?: "English", cow)
            val severe = answer.contains("urgent", ignoreCase = true) || answer.contains("vet", ignoreCase = true)
            state = state.copy(
                aiMessages = state.aiMessages + ("Farmer" to prompt) + ("AI Vet" to answer),
                aiAlerts = if (severe) (state.aiAlerts + "Possible emergency: $prompt").takeLast(4) else state.aiAlerts
            )
            symptomText = ""
        }
    }

    fun heatCycleAdvice(cow: Cattle): String {
        if (cow.lastHeatDate <= 0) return "Add last heat date to predict the next cycle."
        val next = cow.lastHeatDate + 21L * 24L * 60L * 60L * 1000L
        val breedingStart = next - 12L * 60L * 60L * 1000L
        val breedingEnd = next + 18L * 60L * 60L * 1000L
        return "Next heat: ${formatDate(next)}. Best breeding window: ${formatDate(breedingStart)} to ${formatDate(breedingEnd)}. Watch for restlessness, mounting, clear mucus, and reduced feed intake."
    }

    suspend fun createPassportPdf(context: Context, cow: Cattle): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "${cow.earTag.ifBlank { cow.id.toString() }}-health-passport.pdf")
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val paint = android.graphics.Paint().apply { textSize = 18f; color = Color.rgb(18, 55, 42) }
        val small = android.graphics.Paint().apply { textSize = 13f; color = Color.rgb(45, 60, 50) }
        val canvas = page.canvas
        canvas.drawText("Gokula-Health Cattle Passport", 48f, 56f, paint)
        canvas.drawText("Owner: ${state.profile?.name ?: "Farmer"} | Village: ${state.profile?.village ?: "-"}", 48f, 90f, small)
        canvas.drawText("Animal: ${cow.name} | Ear Tag: ${cow.earTag} | Breed: ${cow.breed}", 48f, 120f, small)
        canvas.drawText("Age: ${cow.age} | Weight: ${cow.weight} kg | Pregnant: ${if (cow.pregnant) "Yes" else "No"}", 48f, 150f, small)
        canvas.drawText("Last heat: ${formatDate(cow.lastHeatDate)}", 48f, 180f, small)
        canvas.drawText("Milk monthly average: ${"%.1f".format(state.monthlyAverage)} L/day", 48f, 210f, small)
        canvas.drawText("Breeding advice: ${heatCycleAdvice(cow).take(85)}", 48f, 240f, small)
        canvas.drawText("Vaccination and medical history are stored offline in Room DB.", 48f, 270f, small)
        document.finishPage(page)
        file.outputStream().use { document.writeTo(it) }
        document.close()
        file
    }

    private fun load() {
        viewModelScope.launch {
            val profile = repository.profile()
            val cows = repository.cattle()
            val selected = state.selectedCowId.takeIf { id -> cows.any { it.id == id } } ?: (cows.firstOrNull()?.id ?: 0)
            val reminders = repository.pendingReminders()
            val todayMilk = repository.todayMilk(startOfToday())
            val pregnant = repository.pregnantCount()
            val due = repository.dueCount(System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L)
            state = state.copy(
                loggedIn = profile != null || state.loggedIn,
                profile = profile,
                cattle = cows,
                reminders = reminders,
                selectedCowId = selected,
                todayMilk = todayMilk,
                pregnantCount = pregnant,
                upcomingCount = due
            )
            loadAnalytics(selected)
        }
    }

    private fun loadAnalytics(cowId: Long) {
        if (cowId == 0L) return
        viewModelScope.launch {
            val seven = repository.lastSeven(cowId)
            val thirty = repository.lastThirty(cowId)
            val sevenAvg = seven.map { it.morningLiters + it.eveningLiters }.averageOrZero()
            val monthAvg = thirty.map { it.morningLiters + it.eveningLiters }.averageOrZero()
            val alerts = buildList {
                addAll(state.aiAlerts)
                if (seven.size >= 2) {
                    val latest = seven.first().morningLiters + seven.first().eveningLiters
                    if (sevenAvg > 0 && latest < sevenAvg * 0.75) add("Milk drop alert for ${selectedCow()?.name ?: "selected cow"}")
                }
                selectedCow()?.let { add(heatCycleAdvice(it)) }
            }.takeLast(5)
            state = state.copy(milkEntries = thirty, sevenDayAverage = sevenAvg, monthlyAverage = monthAvg, aiAlerts = alerts)
        }
    }

    private suspend fun maybeNotifyMilkDrop(cow: Cattle, todayTotal: Double) {
        val average = repository.lastSeven(cow.id).map { it.morningLiters + it.eveningLiters }.averageOrZero()
        if (average > 0 && todayTotal < average * 0.75) {
            sendLocalNotification("Milk drop alert", "${cow.name}'s yield is below recent average")
        }
    }

    private fun scheduleReminder(requestCode: Int, cowName: String, vaccineName: String, dueAt: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("cowName", cowName)
            putExtra("vaccineName", vaccineName)
        }
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = maxOf(dueAt + 9L * 60L * 60L * 1000L, System.currentTimeMillis() + 10_000L)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun sendLocalNotification(title: String, message: String) {
        val channelId = ReminderReceiver.CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Gokula-Health alerts", NotificationManager.IMPORTANCE_HIGH)
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, channelId)
        } else {
            android.app.Notification.Builder(context)
        }.setSmallIcon(R.drawable.ic_cow).setContentTitle(title).setContentText(message).setAutoCancel(true).build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun selectedCow(): Cattle? = state.cattle.firstOrNull { it.id == state.selectedCowId }
    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private fun parseDate(value: String): Long = runCatching { dateFormat.parse(value)?.time ?: 0L }.getOrDefault(0L)
    private fun formatDate(time: Long): String = if (time <= 0) "-" else dateFormat.format(Date(time))

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GokulaViewModel(context) as T
    }
}

class GokulaRepository(private val db: AppDatabase) {
    suspend fun profile(): FarmerProfile? = io { db.farmerProfileDao().get() }
    suspend fun saveProfile(profile: FarmerProfile) = io { db.farmerProfileDao().save(profile) }
    suspend fun cattle(): List<Cattle> = io { db.cattleDao().getAll() }
    suspend fun searchCattle(query: String): List<Cattle> = io { if (query.isBlank()) db.cattleDao().getAll() else db.cattleDao().search(query) }
    suspend fun insertCow(cow: Cattle) = io { db.cattleDao().insert(cow) }
    suspend fun deleteCow(cow: Cattle) = io { db.cattleDao().delete(cow) }
    suspend fun insertMilk(entry: MilkEntry) = io { db.milkEntryDao().insert(entry) }
    suspend fun lastSeven(cowId: Long): List<MilkEntry> = io { db.milkEntryDao().lastSevenForCow(cowId) }
    suspend fun lastThirty(cowId: Long): List<MilkEntry> = io { db.milkEntryDao().lastThirtyForCow(cowId) }
    suspend fun insertReminder(reminder: Vaccination): Long = io { db.vaccinationDao().insert(reminder) }
    suspend fun updateReminder(reminder: Vaccination) = io { db.vaccinationDao().update(reminder) }
    suspend fun pendingReminders(): List<Vaccination> = io { db.vaccinationDao().pending() }
    suspend fun dueCount(until: Long): Int = io { db.vaccinationDao().dueCount(until) }
    suspend fun pregnantCount(): Int = io { db.cattleDao().pregnantCount() }
    suspend fun todayMilk(date: Long): Double = io { db.milkEntryDao().totalForDate(date) ?: 0.0 }
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}

interface VeterinaryAiClient {
    suspend fun answer(symptoms: String, language: String, cow: Cattle?): String
}

class LocalVeterinaryAiClient : VeterinaryAiClient {
    override suspend fun answer(symptoms: String, language: String, cow: Cattle?): String {
        val text = symptoms.lowercase()
        val prefix = if (language.contains("Kannada", true)) "ಸರಳ ಸಲಹೆ: " else if (language.contains("Hindi", true)) "सरल सलाह: " else "Simple advice: "
        return when {
            "not eating" in text || "eating" in text || "feed" in text -> prefix + "Check temperature, clean water, fodder freshness, and mouth wounds. If the animal has fever, swelling, or refuses food for more than 12 hours, call a vet urgently."
            "milk" in text || "reduced" in text -> prefix + "Compare today with the 7-day average, check feed, water, heat stress, mastitis signs, and recent vaccination. Red or clotted milk needs urgent veterinary help."
            "fever" in text -> prefix + "Keep the cow shaded, offer water, note temperature, and watch breathing. Fever after vaccination can be mild, but high fever, swelling, or weakness needs a vet urgently."
            "heat" in text || "breeding" in text -> prefix + "Heat often repeats around 21 days. Best breeding is usually 12-18 hours after clear standing heat signs."
            else -> prefix + "Observe appetite, milk, temperature, dung, and behavior. Keep records in the health passport and call a vet if symptoms are severe or sudden."
        }
    }
}

interface CloudSyncService {
    suspend fun enqueueProfile(profile: FarmerProfile)
}

class FirebaseCloudSyncService : CloudSyncService {
    override suspend fun enqueueProfile(profile: FarmerProfile) {
        // Integration point for Firebase Auth, Firestore, Storage, FCM, and backup sync.
    }
}

data class CattleForm(
    val name: String,
    val earTag: String,
    val breed: String,
    val age: String,
    val weight: String,
    val gender: String,
    val photoUri: String,
    val purchaseDate: String,
    val pregnant: Boolean,
    val lastHeatDate: String,
    val breedingStatus: String
)

@Composable
fun GokulaApp(vm: GokulaViewModel, onVoiceInput: () -> Unit, onSharePassport: (Cattle) -> Unit) {
    val state = vm.state
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Page) {
            when {
                !state.splashDone -> SplashScreen()
                !state.loggedIn -> LoginScreen(vm)
                else -> MainShell(vm, onVoiceInput, onSharePassport)
            }
        }
    }
}

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Page), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Gokula-Health", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Smart Dairy Health Companion", color = Muted, fontSize = 17.sp)
        }
    }
}

@Composable
fun LoginScreen(vm: GokulaViewModel) {
    var mobile by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var village by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("English") }
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text("Gokula-Health", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Login with farmer details", color = Muted)
        Spacer(Modifier.height(4.dp))
        Field("Mobile number", mobile) { mobile = it }
        Field("OTP", otp) { otp = it }
        Field("Farmer name", name) { name = it }
        Field("Village", village) { village = it }
        Field("Language: English / Kannada / Hindi", language) { language = it }
        Spacer(Modifier.height(4.dp))
        PrimaryButton("Login / Create Profile") { vm.login(mobile, otp, name, village, language) }
    }
}

@Composable
fun MainShell(vm: GokulaViewModel, onVoiceInput: () -> Unit, onSharePassport: (Cattle) -> Unit) {
    val state = vm.state
    Column(Modifier.fillMaxSize().background(Page)) {
        Header(state)
        Box(Modifier.weight(1f).padding(12.dp)) {
            when (state.screen) {
                Screen.Home -> DashboardScreen(vm)
                Screen.Cattle -> CattleScreen(vm, onSharePassport)
                Screen.Milk -> MilkScreen(vm)
                Screen.Reminders -> ReminderScreen(vm)
                Screen.Breeding -> BreedingScreen(vm)
                Screen.AI -> AiScreen(vm, onVoiceInput)
                Screen.Analytics -> AnalyticsScreen(vm)
                Screen.Profile -> ProfileScreen(vm)
            }
        }
        AppNavBar(selected = state.screen, onSelect = vm::setScreen)
    }
}

@Composable
fun Header(state: AppUiState) {
    Column(Modifier.fillMaxWidth().background(CardSurface).border(1.dp, Line).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text("Gokula-Health", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("${state.profile?.name ?: "Farmer"} • ${state.profile?.village ?: "Village"}", color = Muted, fontSize = 14.sp)
    }
}

@Composable
fun AppNavBar(selected: Screen, onSelect: (Screen) -> Unit) {
    val screens = listOf(Screen.Home, Screen.Cattle, Screen.Milk, Screen.Reminders, Screen.Breeding, Screen.AI, Screen.Analytics, Screen.Profile)
    Row(
        Modifier
            .fillMaxWidth()
            .background(CardSurface)
            .border(1.dp, Line)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        screens.forEach { screen ->
            NavItem(screen = screen, selected = selected == screen, onClick = { onSelect(screen) })
        }
    }
}

@Composable
fun NavItem(screen: Screen, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) SelectedSurface else CardSurface
    val tint = if (selected) Accent else Muted
    Column(
        Modifier
            .widthIn(min = 70.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Image(
            painter = painterResource(iconFor(screen)),
            contentDescription = screen.label,
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(tint)
        )
        Text(screen.label, fontSize = 11.sp, color = tint, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun iconFor(screen: Screen): Int = when (screen) {
    Screen.Home -> R.drawable.ic_home
    Screen.Cattle -> R.drawable.ic_cow
    Screen.Milk -> R.drawable.ic_milk
    Screen.Reminders -> R.drawable.ic_syringe
    Screen.Breeding -> R.drawable.ic_breeding
    Screen.AI -> R.drawable.ic_chat
    Screen.Analytics -> R.drawable.ic_chart
    Screen.Profile -> R.drawable.ic_profile
}

@Composable
fun DashboardScreen(vm: GokulaViewModel) {
    val state = vm.state
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Cow", "Cattle", state.cattle.size.toString(), Modifier.weight(1f))
                StatCard("Milk", "Today", "%.1f L".format(state.todayMilk), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Syringe", "Due", state.upcomingCount.toString(), Modifier.weight(1f))
                StatCard("Calendar", "Pregnant", state.pregnantCount.toString(), Modifier.weight(1f))
            }
        }
        item { SectionCard("AI Alerts") { BulletList(if (state.aiAlerts.isEmpty()) listOf("No critical alerts. Keep daily milk and heat records updated.") else state.aiAlerts) } }
        item { SectionCard("Fast Workflow") { Text("Register cattle, add milk, set vaccine reminders, and export health passports from the tabs above.", color = Ink) } }
    }
}

@Composable
fun CattleScreen(vm: GokulaViewModel, onSharePassport: (Cattle) -> Unit) {
    var name by remember { mutableStateOf("") }
    var earTag by remember { mutableStateOf("") }
    var breed by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Female") }
    var photoUri by remember { mutableStateOf("") }
    var purchaseDate by remember { mutableStateOf(todayText()) }
    var pregnant by remember { mutableStateOf(false) }
    var lastHeat by remember { mutableStateOf(todayText()) }
    var breedingStatus by remember { mutableStateOf("Open") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionCard("Add Cattle") {
                Field("Animal name", name) { name = it }
                Field("Ear Tag ID", earTag) { earTag = it }
                Field("Breed", breed) { breed = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Age", age, Modifier.weight(1f)) { age = it }
                    Field("Weight kg", weight, Modifier.weight(1f)) { weight = it }
                }
                Field("Gender", gender) { gender = it }
                Field("Photo URI", photoUri) { photoUri = it }
                Field("Purchase date yyyy-MM-dd", purchaseDate) { purchaseDate = it }
                Field("Last heat date yyyy-MM-dd", lastHeat) { lastHeat = it }
                Field("Breeding status", breedingStatus) { breedingStatus = it }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(pregnant, { pregnant = it }); Text("Pregnant") }
                PrimaryButton("Save Cattle") {
                    vm.saveCow(CattleForm(name, earTag, breed, age, weight, gender, photoUri, purchaseDate, pregnant, lastHeat, breedingStatus))
                    name = ""; earTag = ""; breed = ""; age = ""; weight = ""; photoUri = ""
                }
            }
        }
        item { Field("Search by ear tag or name", vm.state.search) { vm.updateSearch(it) } }
        items(vm.state.cattle) { cow ->
            CattleCard(cow, selected = cow.id == vm.state.selectedCowId, onSelect = { vm.selectCow(cow.id) }, onDelete = { vm.deleteCow(cow) }, onShare = { onSharePassport(cow) }, advice = vm.heatCycleAdvice(cow))
        }
    }
}

@Composable
fun MilkScreen(vm: GokulaViewModel) {
    var morning by remember { mutableStateOf("") }
    var evening by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CowSelector(vm)
        SectionCard("Milk Diary") {
            Field("Morning milk liters", morning) { morning = it }
            Field("Evening milk liters", evening) { evening = it }
            Field("Notes", notes) { notes = it }
            Text("Daily total: %.1f L".format((morning.toDoubleOrNull() ?: 0.0) + (evening.toDoubleOrNull() ?: 0.0)), color = Accent, fontWeight = FontWeight.Bold)
            PrimaryButton("Save Milk Entry") { vm.saveMilk(morning, evening, notes); morning = ""; evening = ""; notes = "" }
        }
        SectionCard("Production Indicators") {
            Text("7-day average: %.1f L/day".format(vm.state.sevenDayAverage), color = Ink)
            Text("Monthly average: %.1f L/day".format(vm.state.monthlyAverage), color = Ink)
            BulletList(listOf("Stable: normal production", "Watch: slight drop", "Alert: major decline"))
        }
    }
}

@Composable
fun ReminderScreen(vm: GokulaViewModel) {
    var vaccine by remember { mutableStateOf("FMD vaccine") }
    var due by remember { mutableStateOf(todayText()) }
    var repeat by remember { mutableStateOf("180") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { CowSelector(vm) }
        item {
            SectionCard("Vaccination & Medical Reminder") {
                Field("Vaccine / checkup name", vaccine) { vaccine = it }
                Field("Due date yyyy-MM-dd", due) { due = it }
                Field("Repeat interval days", repeat) { repeat = it }
                PrimaryButton("Schedule Offline Reminder") { vm.saveReminder(vaccine, due, repeat) }
            }
        }
        items(vm.state.reminders) { reminder ->
            SectionCard(reminder.vaccineName) {
                Text("Due: ${dateText(reminder.dueDate)} • Repeat: ${reminder.repeatDays} days", color = Ink)
                OutlinedButton(onClick = { vm.markCompleted(reminder) }) { Text("Mark Completed") }
            }
        }
    }
}

@Composable
fun BreedingScreen(vm: GokulaViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { CowSelector(vm) }
        items(vm.state.cattle) { cow ->
            SectionCard("${cow.name} Breeding Tracker") {
                Text("Last heat: ${dateText(cow.lastHeatDate)}", color = Ink)
                Text("Status: ${cow.breedingStatus ?: "Open"}", color = Ink)
                Text(vm.heatCycleAdvice(cow), color = Accent)
            }
        }
    }
}

@Composable
fun AiScreen(vm: GokulaViewModel, onVoiceInput: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CowSelector(vm)
        SectionCard("GenAI Veterinary Assistant") {
            Field("Symptoms, e.g. cow not eating", vm.symptomText) { vm.updateSymptomText(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Ask AI", Modifier.weight(1f)) { vm.askAi() }
                OutlinedButton(onClick = onVoiceInput, modifier = Modifier.weight(1f)) { Text("Voice Input") }
            }
        }
        vm.state.aiMessages.forEach { (speaker, message) ->
            SectionCard(speaker) { Text(message, color = if (speaker == "AI Vet") Ink else Accent) }
        }
    }
}

@Composable
fun AnalyticsScreen(vm: GokulaViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CowSelector(vm)
        SectionCard("MPAndroidChart Analytics") {
            Text("7-day average: %.1f L/day • 30-day average: %.1f L/day".format(vm.state.sevenDayAverage, vm.state.monthlyAverage), color = Ink)
            YieldChart(vm.state.milkEntries)
        }
    }
}

@Composable
fun ProfileScreen(vm: GokulaViewModel) {
    val profile = vm.state.profile
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionCard("Farmer Profile") {
            Text("Name: ${profile?.name ?: "-"}", color = Ink)
            Text("Village: ${profile?.village ?: "-"}", color = Ink)
            Text("Mobile: ${profile?.mobile ?: "-"}", color = Ink)
            Text("Language: ${profile?.language ?: "English"}", color = Ink)
            Text("Cattle count: ${vm.state.cattle.size}", color = Ink)
            OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) {
                Text("Logout")
            }
        }
        SectionCard("Cloud & Backup") {
            BulletList(listOf("Firebase Auth/Firestore/Storage integration point added", "Offline Room DB remains the source of truth", "Sync queue can be connected when google-services.json is added"))
        }
    }
}

@Composable
fun CattleCard(cow: Cattle, selected: Boolean, onSelect: () -> Unit, onDelete: () -> Unit, onShare: () -> Unit, advice: String) {
    SectionCard("${if (selected) "Selected • " else ""}${cow.name}") {
        Text("Ear Tag: ${cow.earTag} • Breed: ${cow.breed}", color = Ink)
        Text("Age: ${cow.age} • Weight: ${cow.weight} kg • ${cow.gender}", color = Ink)
        Text("Pregnancy: ${if (cow.pregnant) "Pregnant" else "Not pregnant"}", color = Ink)
        Text("Health passport: vaccination, milk, breeding, treatment and owner records", color = Accent)
        QrCode(cow.earTag.ifBlank { cow.id.toString() })
        Text(advice, color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSelect, modifier = Modifier.weight(1f)) { Text("Open") }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("PDF/WhatsApp") }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete") }
        }
    }
}

@Composable
fun CowSelector(vm: GokulaViewModel) {
    SectionCard("Select Animal") {
        if (vm.state.cattle.isEmpty()) {
            Text("Add cattle first.", color = Ink)
        } else {
            vm.state.cattle.forEach { cow ->
                TextButton(onClick = { vm.selectCow(cow.id) }) {
                    Text("${if (cow.id == vm.state.selectedCowId) "✓ " else ""}${cow.name} (${cow.earTag})", color = Ink)
                }
            }
        }
    }
}

@Composable
fun YieldChart(entries: List<MilkEntry>) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(280.dp),
        factory = { context ->
            LineChart(context).apply {
                description = Description().apply { text = "7 / 30 day milk trend" }
                setNoDataText("Add milk entries to view trend")
            }
        },
        update = { chart ->
            val ordered = entries.reversed()
            val points = ordered.mapIndexed { index, entry -> Entry((index + 1).toFloat(), (entry.morningLiters + entry.eveningLiters).toFloat()) }
            val latest = points.lastOrNull()?.y ?: 0f
            val avg = points.map { it.y }.averageFloatOrZero().toFloat()
            val color = when {
                avg == 0f || latest >= avg * 0.9f -> Color.rgb(31, 122, 77)
                latest >= avg * 0.75f -> Color.rgb(217, 154, 41)
                else -> Color.rgb(192, 57, 43)
            }
            val set = LineDataSet(points, "Milk liters").apply {
                setColor(color); setCircleColor(color); lineWidth = 3f; circleRadius = 4f; valueTextSize = 10f
            }
            chart.data = LineData(set)
            chart.invalidate()
        }
    )
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun StatCard(icon: String, label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, color = Muted, fontWeight = FontWeight.Medium)
            Text(value, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun Field(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(), singleLine = false)
}

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
        Text(label, color = androidx.compose.ui.graphics.Color.White)
    }
}

@Composable
fun BulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { Text("• $it", color = Ink) }
    }
}

@Composable
fun QrCode(seed: String) {
    val bits = seed.hashCode().absoluteValue
    Canvas(Modifier.size(86.dp).border(1.dp, Line).padding(4.dp)) {
        val cell = size.width / 9f
        for (x in 0 until 9) for (y in 0 until 9) {
            val on = x < 2 && y < 2 || x > 6 && y < 2 || x < 2 && y > 6 || ((bits shr ((x + y * 3) % 24)) and 1) == 1
            if (on) drawRect(Ink, topLeft = androidx.compose.ui.geometry.Offset(x * cell, y * cell), size = androidx.compose.ui.geometry.Size(cell * .85f, cell * .85f), style = Fill)
        }
    }
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
private fun List<Float>.averageFloatOrZero(): Double = if (isEmpty()) 0.0 else map { it.toDouble() }.average()
private fun todayText(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
private fun dateText(time: Long): String = if (time <= 0) "-" else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))
