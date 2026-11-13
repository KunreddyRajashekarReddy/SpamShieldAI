
package com.deepak.spamshieldai

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val BASE_URL = "http://127.0.0.1:8000"
private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

private val Navy = Color(0xFF10233E)
private val TileBlue = Color(0xFF182A43)
private val Mint = Color(0xFF79E6C1)
private val SoftWhite = Color(0xFFD2DEEA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SpamShieldScreen(this)
            }
        }
    }
}

data class ScanResult(
    val category: String,
    val riskScore: Int,
    val riskLevel: String,
    val explanation: String,
    val aiStatus: String
)

data class ScanRecord(
    val id: Int,
    val timestamp: String,
    val category: String,
    val input: String,
    val result: ScanResult
)

private fun parseResult(j: JSONObject) = ScanResult(
    j.optString("category", "Unknown"),
    j.optInt("risk_score", 0).coerceIn(0, 100),
    j.optString("risk_level", "Unknown"),
    j.optString("explanation", "No explanation provided"),
    j.optString("ai_status", "")
)

private fun parseRecord(j: JSONObject) = ScanRecord(
    j.optInt("id"),
    j.optString("timestamp"),
    j.optString("category"),
    j.optString("input"),
    parseResult(j)
)

private fun connection(path: String): HttpURLConnection =
    (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 60000
        useCaches = false
    }

private fun responseText(conn: HttpURLConnection): String {
    val status = conn.responseCode
    val body = (
            if (status in 200..299) conn.inputStream
            else conn.errorStream
            )?.bufferedReader()?.use { it.readText() } ?: ""

    if (status !in 200..299) {
        throw IllegalStateException(
            "HTTP $status: ${body.take(350)}"
        )
    }
    return body
}

suspend fun checkBackend(): String =
    withContext(Dispatchers.IO) {
        val conn = connection("/")
        try {
            conn.requestMethod = "GET"
            JSONObject(responseText(conn))
                .optString("message", "Backend online")
        } finally {
            conn.disconnect()
        }
    }

suspend fun scanText(
    path: String,
    key: String,
    text: String
): ScanResult = withContext(Dispatchers.IO) {
    val conn = connection(path)
    try {
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty(
            "Content-Type",
            "application/json; charset=utf-8"
        )
        val bytes = JSONObject()
            .put(key, text)
            .toString()
            .toByteArray(Charsets.UTF_8)

        conn.outputStream.use { it.write(bytes) }
        parseResult(JSONObject(responseText(conn)))
    } finally {
        conn.disconnect()
    }
}

suspend fun scanImage(
    activity: ComponentActivity,
    uri: Uri
): ScanResult = withContext(Dispatchers.IO) {
    val resolver = activity.contentResolver
    val mime = resolver.getType(uri) ?: ""

    require(mime in setOf(
        "image/png",
        "image/jpeg",
        "image/webp"
    )) {
        "Select a PNG, JPEG, or WebP image"
    }

    val bytes = resolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)

        while (true) {
            val n = input.read(buffer)
            if (n == -1) break

            require(out.size() + n <= MAX_IMAGE_BYTES) {
                "Image must be under 5 MB"
            }
            out.write(buffer, 0, n)
        }
        out.toByteArray()
    } ?: error("Cannot read image")

    require(bytes.isNotEmpty()) { "Image is empty" }

    val boundary = "SpamShield-${UUID.randomUUID()}"
    val filename = when (mime) {
        "image/png" -> "scan.png"
        "image/webp" -> "scan.webp"
        else -> "scan.jpg"
    }

    val conn = connection("/analyze-image")
    try {
        conn.requestMethod = "POST"
        conn.readTimeout = 180000
        conn.doOutput = true
        conn.setRequestProperty(
            "Content-Type",
            "multipart/form-data; boundary=$boundary"
        )

        conn.outputStream.use { out ->
            out.write(
                ("--$boundary\r\n" +
                        "Content-Disposition: form-data; " +
                        "name=\"file\"; filename=\"$filename\"\r\n" +
                        "Content-Type: $mime\r\n\r\n").toByteArray()
            )
            out.write(bytes)
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }

        parseResult(JSONObject(responseText(conn)))
    } finally {
        conn.disconnect()
    }
}

suspend fun fetchHistory(): List<ScanRecord> =
    withContext(Dispatchers.IO) {
        val conn = connection("/history")
        try {
            conn.requestMethod = "GET"
            val arr = JSONArray(responseText(conn))
            List(arr.length()) {
                parseRecord(arr.getJSONObject(it))
            }
        } finally {
            conn.disconnect()
        }
    }

@Composable
private fun Heading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ErrorText(error: String) {
    if (error.isNotBlank()) {
        Text(
            error,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ResultCard(result: ScanResult) {
    val valid = !result.riskLevel.equals("Unknown", true) &&
            !result.aiStatus.equals("unavailable", true)

    val color = when {
        !valid -> Color(0xFF9E9E9E)
        result.riskLevel.equals("High", true) ||
                result.riskScore >= 70 -> Color(0xFFE53935)
        result.riskLevel.equals("Medium", true) ||
                result.riskScore >= 40 -> Color(0xFFFF9800)
        else -> Color(0xFF2EAD79)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Heading("AI Analysis Result")

            Text(
                "Category: ${result.category}",
                fontWeight = FontWeight.SemiBold
            )

            if (valid) {
                Text(
                    "Risk: ${result.riskLevel}",
                    color = color,
                    fontWeight = FontWeight.Bold
                )
                Text("Risk Score: ${result.riskScore}/100")

                LinearProgressIndicator(
                    progress = {
                        result.riskScore / 100f
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = color
                )
            } else {
                Text(
                    "Risk unknown — analysis unavailable",
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(result.explanation)

            if (result.aiStatus.isNotBlank()) {
                Text(
                    "Method: ${result.aiStatus}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                "Automated analysis does not guarantee safety.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "$count",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FeatureTile(
    symbol: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = TileBlue
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                symbol,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                color = SoftWhite,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// -----------------------------------------------------
// AUTOMATIC CALL GUARDIAN
// Simulated call only. No real call audio is captured.
// -----------------------------------------------------

private val guardianConversation = listOf(
    "Caller: Hello, I am calling about your package delivery.",
    "You: Okay, which delivery company?",
    "Caller: Your parcel has been delayed. I need to verify some details.",
    "Caller: Actually, I am a CBI officer. Your Aadhaar is linked to a criminal case.",
    "You: What? I haven't done anything wrong.",
    "Caller: You are under digital arrest. Do not disconnect or tell your family.",
    "Caller: Transfer Rs. 50,000 immediately and share your OTP."
)

@Composable
private fun CallGuardianScreen(
    onHistoryUpdated: () -> Unit
) {
    val analysisScope = rememberCoroutineScope()

    // -1 incoming, 0 active, 1 ended
    var callState by remember { mutableIntStateOf(-1) }
    var step by remember { mutableIntStateOf(-1) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var error by remember { mutableStateOf("") }
    var analyzing by remember { mutableStateOf(false) }
    var familyAlert by remember { mutableStateOf(false) }
    var analysisStarted by remember { mutableStateOf(false) }

    LaunchedEffect(callState) {
        if (callState == 0) {
            for (index in guardianConversation.indices) {
                step = index
                delay(2500)
            }
        }
    }

    // Network request runs independently of transcript changes.
    LaunchedEffect(callState, step) {
        if (
            callState == 0 &&
            step >= 5 &&
            !analysisStarted
        ) {
            analysisStarted = true

            val transcript = guardianConversation
                .take(step + 1)
                .joinToString("\n")

            analysisScope.launch {
                analyzing = true
                error = ""

                try {
                    result = scanText(
                        "/analyze-text",
                        "text",
                        transcript
                    )
                    onHistoryUpdated()
                } catch (e: Exception) {
                    error = "AI analysis failed: ${e.message}"
                } finally {
                    analyzing = false
                }
            }
        }
    }

    Heading("AI Call Guardian")

    Text(
        "SIMULATED CALL DEMO — not real phone monitoring.",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "📞 Unknown Caller",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            when (callState) {
                -1 -> {
                    Text("Incoming simulated call...")

                    Button(
                        onClick = {
                            callState = 0
                            step = -1
                            result = null
                            error = ""
                            analyzing = false
                            familyAlert = false
                            analysisStarted = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📞 Accept Call")
                    }

                    OutlinedButton(
                        onClick = { callState = 1 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reject Call")
                    }
                }

                0 -> {
                    Text(
                        "● Simulated call in progress",
                        color = Color(0xFF2EAD79)
                    )

                    Text(
                        "Live-style transcript",
                        fontWeight = FontWeight.Bold
                    )

                    guardianConversation
                        .take((step + 1).coerceAtLeast(0))
                        .forEach { line ->
                            Text(line)
                            HorizontalDivider()
                        }

                    if (step < guardianConversation.lastIndex) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Conversation continuing automatically...")
                    } else {
                        Text("Simulated conversation complete.")
                    }

                    if (analyzing) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Gemini is analyzing scam indicators...")
                    }

                    ErrorText(error)

                    result?.let { analysis ->
                        ResultCard(analysis)

                        val validAnalysis =
                            !analysis.riskLevel.equals("Unknown", true) &&
                                    !analysis.aiStatus.equals("unavailable", true)

                        val highRisk =
                            analysis.riskLevel.equals("High", true) ||
                                    analysis.riskScore >= 70

                        if (validAnalysis && highRisk) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor =
                                        MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement =
                                        Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        "🚨 POTENTIAL SCAM DETECTED",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )

                                    Text(
                                        "Possible digital-arrest scam. " +
                                                "Do not transfer money or share OTPs."
                                    )

                                    Text(
                                        "End the conversation and verify " +
                                                "the caller independently."
                                    )

                                    Button(
                                        onClick = {
                                            familyAlert = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("🚨 Simulate Family Alert")
                                    }

                                    if (familyAlert) {
                                        Text(
                                            "Family alert simulated. " +
                                                    "No real notification was sent."
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { callState = 1 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📵 End Simulated Call")
                    }
                }

                1 -> {
                    Text("Simulated call ended.")

                    OutlinedButton(
                        onClick = {
                            callState = -1
                            step = -1
                            result = null
                            error = ""
                            analyzing = false
                            familyAlert = false
                            analysisStarted = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Restart Demo")
                    }
                }
            }
        }
    }

    Text(
        "The conversation is prewritten. Gemini analysis " +
                "starts automatically when scam indicators appear. " +
                "No real call audio is captured.",
        style = MaterialTheme.typography.bodySmall
    )
}

// -----------------------------------------------------
// MAIN APPLICATION
// -----------------------------------------------------

@Composable
private fun SpamShieldScreen(activity: ComponentActivity) {
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }

    var sms by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    var smsResult by remember { mutableStateOf<ScanResult?>(null) }
    var urlResult by remember { mutableStateOf<ScanResult?>(null) }
    var imageResult by remember { mutableStateOf<ScanResult?>(null) }

    var smsError by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf("") }
    var imageError by remember { mutableStateOf("") }

    var smsBusy by remember { mutableStateOf(false) }
    var urlBusy by remember { mutableStateOf(false) }
    var imageBusy by remember { mutableStateOf(false) }

    var history by remember {
        mutableStateOf<List<ScanRecord>>(emptyList())
    }
    var historyLoaded by remember { mutableStateOf(false) }
    var historyBusy by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf("") }

    var backendStatus by remember { mutableStateOf("Not checked") }
    var backendBusy by remember { mutableStateOf(false) }

    fun refreshHistory() {
        scope.launch {
            historyBusy = true
            historyError = ""

            try {
                history = fetchHistory()
                historyLoaded = true
            } catch (e: Exception) {
                historyLoaded = false
                historyError = "History unavailable: ${e.message}"
            } finally {
                historyBusy = false
            }
        }
    }

    fun testConnection() {
        scope.launch {
            backendBusy = true
            backendStatus = "Checking..."

            try {
                backendStatus = "Connected: ${checkBackend()}"
            } catch (e: Exception) {
                backendStatus = "Connection failed: ${e.message}"
            } finally {
                backendBusy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        testConnection()
        refreshHistory()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) {
        imageUri = it
        imageResult = null
        imageError = ""
    }

    val tabs = listOf(
        "Dashboard", "SMS", "URL", "Image", "History"
    )
    val icons = listOf(
        "⌂", "✉", "🔗", "▧", "◷"
    )

    Scaffold(
        topBar = {
            Surface(
                color = Navy,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        "🛡️ SpamShieldAI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Powered by Google Gemini AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mint
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, name ->
                    NavigationBarItem(
                        selected = tab == i || (tab == 5 && i == 0),
                        onClick = {
                            tab = i
                            if (i == 4) refreshHistory()
                        },
                        icon = { Text(icons[i]) },
                        label = {
                            Text(
                                name,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (tab) {

                // DASHBOARD
                0 -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Navy
                        ),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "🛡️ SPAMSHIELDAI",
                                color = Mint,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "Your AI Scam Protection",
                                color = Color.White,
                                style =
                                    MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "Analyze suspicious calls, messages, " +
                                        "links and screenshots with Gemini AI.",
                                color = SoftWhite
                            )

                            Text(
                                backendStatus,
                                color =
                                    if (backendStatus.startsWith("Connected"))
                                        Mint
                                    else
                                        Color(0xFFFFC68A)
                            )

                            OutlinedButton(
                                onClick = { testConnection() },
                                enabled = !backendBusy
                            ) {
                                Text(
                                    "Check Backend",
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Heading("Protection Tools")

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureTile(
                            "📞",
                            "Call Guardian",
                            "Automatic scam demo",
                            { tab = 5 },
                            Modifier.weight(1f)
                        )

                        FeatureTile(
                            "💬",
                            "SMS Scanner",
                            "Analyze messages",
                            { tab = 1 },
                            Modifier.weight(1f)
                        )
                    }

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureTile(
                            "🔗",
                            "URL Scanner",
                            "Detect phishing",
                            { tab = 2 },
                            Modifier.weight(1f)
                        )

                        FeatureTile(
                            "🖼️",
                            "Image Scanner",
                            "Check screenshots",
                            { tab = 3 },
                            Modifier.weight(1f)
                        )
                    }

                    Heading("Detection Overview")

                    if (historyBusy) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (historyLoaded) {
                        val high = history.count {
                            it.result.riskLevel.equals("High", true) &&
                                    !it.result.aiStatus.equals(
                                        "unavailable", true
                                    )
                        }

                        val medium = history.count {
                            it.result.riskLevel.equals("Medium", true) &&
                                    !it.result.aiStatus.equals(
                                        "unavailable", true
                                    )
                        }

                        val low = history.count {
                            it.result.riskLevel.equals("Low", true) &&
                                    !it.result.aiStatus.equals(
                                        "unavailable", true
                                    )
                        }

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                "Total Scans",
                                history.size,
                                Modifier.weight(1f)
                            )
                            StatCard(
                                "High Risk",
                                high,
                                Modifier.weight(1f)
                            )
                        }

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                "Medium Risk",
                                medium,
                                Modifier.weight(1f)
                            )
                            StatCard(
                                "Low Risk",
                                low,
                                Modifier.weight(1f)
                            )
                        }

                        val unknown =
                            history.size - high - medium - low

                        if (unknown > 0) {
                            Text(
                                "Unknown / unavailable: $unknown"
                            )
                        }

                        Text(
                            "Statistics reflect current backend history.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        ErrorText(
                            historyError.ifBlank {
                                "Statistics not loaded yet."
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = { refreshHistory() },
                        enabled = !historyBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refresh Dashboard")
                    }

                    Button(
                        onClick = {
                            refreshHistory()
                            tab = 4
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Detection History")
                    }

                    Text(
                        "Call Guardian is a simulated demo, " +
                                "not live phone monitoring.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // SMS SCANNER
                1 -> {
                    Heading("💬 SMS / Message Analysis")

                    Text(
                        "Check suspicious messages for fraud, " +
                                "phishing and social engineering."
                    )

                    OutlinedTextField(
                        value = sms,
                        onValueChange = {
                            sms = it
                            smsResult = null
                            smsError = ""
                        },
                        label = {
                            Text("Enter suspicious message")
                        },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                smsBusy = true
                                smsError = ""
                                smsResult = null

                                try {
                                    smsResult = scanText(
                                        "/analyze-text",
                                        "text",
                                        sms
                                    )
                                    refreshHistory()
                                } catch (e: Exception) {
                                    smsError =
                                        "SMS analysis failed: ${e.message}"
                                } finally {
                                    smsBusy = false
                                }
                            }
                        },
                        enabled = sms.isNotBlank() && !smsBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (smsBusy) "Analyzing..."
                            else "Analyze Message"
                        )
                    }

                    if (smsBusy) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ErrorText(smsError)
                    smsResult?.let { ResultCard(it) }

                    OutlinedButton(
                        onClick = {
                            sms = ""
                            smsResult = null
                            smsError = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear Message")
                    }
                }

                // URL SCANNER
                2 -> {
                    Heading("🔗 Phishing URL Detection")

                    Text(
                        "Analyze suspicious links before opening them."
                    )

                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            urlResult = null
                            urlError = ""
                        },
                        label = {
                            Text("Enter suspicious URL")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                urlBusy = true
                                urlError = ""
                                urlResult = null

                                try {
                                    urlResult = scanText(
                                        "/analyze-url",
                                        "url",
                                        url
                                    )
                                    refreshHistory()
                                } catch (e: Exception) {
                                    urlError =
                                        "URL analysis failed: ${e.message}"
                                } finally {
                                    urlBusy = false
                                }
                            }
                        },
                        enabled = url.isNotBlank() && !urlBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (urlBusy) "Checking..."
                            else "Check Suspicious URL"
                        )
                    }

                    if (urlBusy) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ErrorText(urlError)
                    urlResult?.let { ResultCard(it) }

                    OutlinedButton(
                        onClick = {
                            url = ""
                            urlResult = null
                            urlError = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear URL")
                    }
                }

                // IMAGE SCANNER
                3 -> {
                    Heading("🖼️ Screenshot Scam Detection")

                    Text(
                        "Select a PNG, JPEG or WebP screenshot " +
                                "(up to 5 MB)."
                    )

                    OutlinedButton(
                        onClick = {
                            picker.launch("image/*")
                        },
                        enabled = !imageBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose Screenshot")
                    }

                    Text(
                        if (imageUri == null)
                            "No image selected"
                        else
                            "Image selected"
                    )

                    Button(
                        onClick = {
                            val selected = imageUri
                                ?: return@Button

                            scope.launch {
                                imageBusy = true
                                imageError = ""
                                imageResult = null

                                try {
                                    imageResult = scanImage(
                                        activity,
                                        selected
                                    )
                                    refreshHistory()
                                } catch (e: Exception) {
                                    imageError =
                                        "Image analysis failed: ${e.message}"
                                } finally {
                                    imageBusy = false
                                }
                            }
                        },
                        enabled = imageUri != null && !imageBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (imageBusy) "Analyzing..."
                            else "Analyze Screenshot"
                        )
                    }

                    if (imageBusy) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ErrorText(imageError)
                    imageResult?.let { ResultCard(it) }

                    Text(
                        "Do not upload real OTPs or sensitive " +
                                "financial details.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // DETECTION HISTORY
                4 -> {
                    Heading("📋 Detection History")

                    OutlinedButton(
                        onClick = { refreshHistory() },
                        enabled = !historyBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refresh History")
                    }

                    if (historyBusy) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ErrorText(historyError)

                    if (historyLoaded) {
                        if (history.isEmpty()) {
                            Text("No scans yet")
                        } else {
                            history.forEach { record ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement =
                                            Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "${record.category} · #${record.id}",
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(
                                            record.timestamp,
                                            style =
                                                MaterialTheme.typography.bodySmall
                                        )

                                        Text(
                                            record.input,
                                            maxLines = 4
                                        )

                                        ResultCard(record.result)
                                    }
                                }
                            }
                        }
                    }
                }

                // CALL GUARDIAN
                5 -> {
                    OutlinedButton(
                        onClick = { tab = 0 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back to Dashboard")
                    }

                    CallGuardianScreen(
                        onHistoryUpdated = {
                            refreshHistory()
                        }
                    )
                }
            }
        }
    }
}
