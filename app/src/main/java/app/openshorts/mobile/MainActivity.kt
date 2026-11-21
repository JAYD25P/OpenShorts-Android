package app.openshorts.mobile

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private val BG = Color(0xFF10121A)
private val CARD = Color(0xFF1B1E29)
private val ACCENT = Color(0xFF9B83FF)
private val SUBTLE = Color(0xFF9AA0B2)

class MainActivity : ComponentActivity() {
    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    private fun securePrefs() = EncryptedSharedPreferences.create(this, "openshorts",
        MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = securePrefs()
        setContent {
            var server by remember { mutableStateOf(prefs.getString("server", "") ?: "") }
            var apiToken by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
            var geminiKey by remember { mutableStateOf(prefs.getString("gemini", "") ?: "") }
            var chosenUri by remember { mutableStateOf<Uri?>(null) }
            var chosenName by remember { mutableStateOf("No video selected") }
            var status by remember { mutableStateOf("Ready to create your first clip") }
            var details by remember { mutableStateOf("Connect to an OpenShorts server to begin.") }
            var jobId by remember { mutableStateOf("") }
            var running by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf("") }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                chosenUri = uri
                chosenName = uri?.let { displayName(it) } ?: "No video selected"
            }
            MaterialTheme(colorScheme = darkColorScheme(primary = ACCENT, background = BG, surface = CARD)) {
                Column(Modifier.fillMaxSize().background(BG).verticalScroll(rememberScrollState()).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = ACCENT, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("OPENSHORTS", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Text("MOBILE STUDIO", color = SUBTLE, fontSize = 10.sp, letterSpacing = 2.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(if (server.isBlank()) "NOT CONNECTED" else "SERVER SET", color = if(server.isBlank()) SUBTLE else Color(0xFF65D6A3), fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(30.dp))
                    Text("Turn long videos into", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("short-form stories.", color = ACCENT, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("AI-powered clips, reframed for vertical viewing.", color = SUBTLE, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(26.dp))
                    Heading("01", "NEW PROJECT")
                    Card(colors = CardDefaults.cardColors(containerColor = CARD), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.VideoFile, null, tint = ACCENT)
                                Spacer(Modifier.width(9.dp))
                                Column {
                                    Text("Source video", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text("Choose a video from your device", color = SUBTLE, fontSize = 11.sp)
                                }
                            }
                            OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                                Icon(Icons.Rounded.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text(chosenName, maxLines = 1)
                            }
                            Button(
                                enabled = chosenUri != null && server.isNotBlank() && !running,
                                onClick = {
                                    val uri = chosenUri ?: return@Button
                                    running = true; error = ""; status = "Uploading video…"; details = "Preparing upload"
                                    lifecycleScope.launch {
                                        try {
                                            val id = withContext(Dispatchers.IO) { uploadAndStart(uri, server, apiToken, geminiKey) { msg -> runOnUiThread { status = msg } } }
                                            jobId = id
                                            details = "Upload complete. OpenShorts has queued your video."
                                            status = "Queued"
                                            watchJob(id, server, apiToken, geminiKey, { status = it }, { details = it }, { running = false })
                                        } catch (e: Exception) {
                                            error = e.message ?: "Unable to start processing"
                                            status = "Job could not start"
                                            running = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (running) CircularProgressIndicator(Modifier.size(18.dp), color = BG, strokeWidth = 2.dp) else Icon(Icons.Rounded.AutoAwesome, null)
                                Spacer(Modifier.width(8.dp)); Text(if(running) "PROCESSING" else "GENERATE SHORTS", fontWeight = FontWeight.Bold)
                            }
                            Text("Only process videos you own or have permission to use.", color = SUBTLE, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Heading("02", "JOB MONITOR")
                    Card(colors = CardDefaults.cardColors(containerColor = CARD), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Timelapse, null, tint = ACCENT)
                                Spacer(Modifier.width(8.dp)); Text("Processing status", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                if(running) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = ACCENT)
                            }
                            Text(status, color = Color.White, modifier = Modifier.padding(top = 10.dp))
                            if(jobId.isNotBlank()) Text("JOB  " + jobId, color = SUBTLE, fontSize = 10.sp)
                            Text(details.takeLast(1500), color = SUBTLE, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                            if(error.isNotBlank()) Text(error, color = Color(0xFFFF8888), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Heading("03", "SERVER CONNECTION")
                    Card(colors = CardDefaults.cardColors(containerColor = CARD), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            SettingField("OpenShorts server URL", server, { server = it; prefs.edit().putString("server",it).apply() }, "https://your-server.example")
                            SettingField("API bearer token (if required)", apiToken, { apiToken = it; prefs.edit().putString("token",it).apply() }, "osk_…")
                            SettingField("Gemini API key (self-hosted BYOK)", geminiKey, { geminiKey = it; prefs.edit().putString("gemini",it).apply() }, "AIza…")
                            Text("Credentials are encrypted on-device. Use HTTPS when connecting over the internet.", color = SUBTLE, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text("ANDROID CLIENT  •  SERVER-SIDE AI PROCESSING", color = SUBTLE, fontSize = 9.sp, letterSpacing = 1.sp)
                }
            }
        }
    }

    private fun headers(builder: Request.Builder, token: String, gemini: String): Request.Builder {
        if(token.isNotBlank()) builder.header("Authorization", "Bearer " + token)
        if(gemini.isNotBlank()) builder.header("X-Gemini-Key", gemini)
        return builder
    }
    private fun root(url: String): String {
        val value = url.trim().trimEnd('/')
        require(value.startsWith("https://") || value.startsWith("http://")) { "Enter a server URL beginning with https:// or http://" }
        return value
    }
    private fun uploadAndStart(uri: Uri, server: String, token: String, gemini: String, progress: (String)->Unit): String {
        val base = root(server)
        val name = displayName(uri)
        val json = JSONObject().put("filename", name).toString().toRequestBody("application/json".toMediaType())
        val reserve = headers(Request.Builder().url(base + "/api/uploads").post(json),token,gemini).build()
        val reservation = http.newCall(reserve).execute().use { res ->
            val body = res.body?.string().orEmpty()
            check(res.isSuccessful) { parseError(body,res.code) }
            JSONObject(body)
        }
        val temp = File(cacheDir, "openshorts_upload_" + System.currentTimeMillis())
        try {
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not read the selected video" }
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            progress("Uploading " + (temp.length()/1048576) + " MB…")
            val put = headers(Request.Builder().url(reservation.getString("upload_url")).put(temp.asRequestBody("application/octet-stream".toMediaType())),token,gemini).build()
            http.newCall(put).execute().use { res ->
                val body = res.body?.string().orEmpty()
                check(res.isSuccessful) { parseError(body,res.code) }
            }
            progress("Starting AI clip detection…")
            val submitBody = JSONObject().put("upload_id",reservation.getString("upload_id")).put("acknowledged",true).put("output_format","vertical").toString().toRequestBody("application/json".toMediaType())
            val submit = headers(Request.Builder().url(base + "/api/process").post(submitBody),token,gemini).build()
            http.newCall(submit).execute().use { res ->
                val body = res.body?.string().orEmpty()
                check(res.isSuccessful) { parseError(body,res.code) }
                JSONObject(body).getString("job_id")
            }
        } finally { temp.delete() }
    }
    private suspend fun watchJob(id: String, server: String, token: String, gemini: String, onStatus: (String)->Unit, onDetails: (String)->Unit, onDone: ()->Unit) {
        withContext(Dispatchers.IO) {
            for (attempt in 0 until 360) {
                val request = headers(Request.Builder().url(root(server) + "/api/status/" + id).get(),token,gemini).build()
                val data = http.newCall(request).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    check(res.isSuccessful) { parseError(body,res.code) }
                    JSONObject(body)
                }
                val current = data.optString("status","processing")
                val logs = data.optJSONArray("logs")
                val message = buildString { if(logs != null) for(i in 0 until logs.length()) append(logs.optString(i)).append('\n') }
                withContext(Dispatchers.Main) {
                    onStatus(current.replaceFirstChar { it.uppercase() })
                    if(message.isNotBlank()) onDetails(message)
                }
                if(current == "completed" || current == "failed" || current == "error") break
                kotlinx.coroutines.delay(5000)
            }
        }
        withContext(Dispatchers.Main) { onDone() }
    }
    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if(c.moveToFirst()) c.getString(0) else "Selected video" } ?: "Selected video"
    private fun parseError(body: String, code: Int): String = try { JSONObject(body).optString("detail").ifBlank { "Server error HTTP " + code } } catch (_: Exception) { "Server error HTTP " + code + ": " + body }
}

@Composable private fun Heading(number: String, label: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number, color = ACCENT, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(Modifier.width(8.dp)); Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp)
    }
}
@Composable private fun SettingField(label: String, value: String, change: (String)->Unit, hint: String) {
    Text(label, color = Color.White, fontSize = 12.sp)
    OutlinedTextField(value, change, modifier = Modifier.fillMaxWidth().padding(top = 4.dp,bottom = 10.dp), singleLine = true, placeholder = { Text(hint) })
}
