package ai.byak.app.ui

import ai.byak.app.auth.GoogleSignIn
import ai.byak.app.data.ApiClient
import ai.byak.app.data.SettingsStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable fun AuthScreen(api: ApiClient, settings: SettingsStore) {
    val server by settings.serverUrl.collectAsState(initial = "")
    var editingServer by remember { mutableStateOf(false) }
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    val emailValid = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())
    val canSubmit = !busy && emailValid && password.length >= (if (register) 10 else 1)

    fun attempt(block: suspend () -> Unit) = scope.launch { busy = true; error = null; try { block() } catch (e: Exception) { error = e.message ?: "Something went wrong" } finally { busy = false } }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(.22f), MaterialTheme.colorScheme.background), radius = 1200f)).imePadding(), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(24.dp).widthIn(max = 440.dp).verticalScroll(rememberScrollState()), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(.96f))) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.padding(12.dp), tint = Color.White) }
                Text("BYAK AI", fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("Bring Your API Key. Bring Your Intelligence.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (register) OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = email.isNotBlank() && !emailValid, supportingText = { if (email.isNotBlank() && !emailValid) Text("Enter a valid email") })
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showPassword) "Hide password" else "Show password") } },
                    supportingText = { if (register) Text("At least 10 characters") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { attempt { if (register) api.register(name.trim(), email.trim(), password) else api.login(email.trim(), password) } }, Modifier.fillMaxWidth().height(52.dp), enabled = canSubmit) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (register) "Create BYAK account" else "Sign in")
                }
                OutlinedButton(onClick = {
                    if (!GoogleSignIn.configured) error = "Google Sign-In isn't enabled in this build."
                    else attempt { GoogleSignIn.idToken(context.findActivity() ?: context)?.let { api.google(it) } }
                }, Modifier.fillMaxWidth(), enabled = !busy) { Icon(Icons.Outlined.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text("Continue with Google") }
                TextButton(onClick = { register = !register; error = null }, Modifier.align(Alignment.CenterHorizontally)) { Text(if (register) "Already registered? Sign in" else "New here? Create account") }
                Text("Your provider keys are encrypted on the server and never stored in the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { editingServer = true }, Modifier.align(Alignment.CenterHorizontally)) { Icon(Icons.Outlined.Dns, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Server: ${server.removePrefix("https://").removePrefix("http://").ifBlank { "not set" }}", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
    if (editingServer) ServerDialog(server, settings, onDismiss = { editingServer = false }) { editingServer = false }
}
