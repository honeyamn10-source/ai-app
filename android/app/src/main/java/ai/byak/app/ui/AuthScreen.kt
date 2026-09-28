package ai.byak.app.ui

import ai.byak.app.ByakApplication
import ai.byak.app.auth.GoogleSignIn
import ai.byak.app.data.Session
import ai.byak.app.data.SessionStore
import androidx.compose.animation.AnimatedVisibility
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

/**
 * First screen. BYAK runs entirely on the phone by default (no server, no account needed);
 * signing in to a self-hosted BYAK server is an advanced option.
 */
@Composable fun AuthScreen(app: ByakApplication) {
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    var useServer by remember { mutableStateOf(false) }

    fun attempt(block: suspend () -> Unit) = scope.launch { busy = true; error = null; try { block() } catch (e: Exception) { error = e.message ?: "Something went wrong" } finally { busy = false } }
    fun startOnDevice(name: String, email: String) = attempt {
        app.local.setIdentity(name, email)
        app.sessionStore.save(Session(SessionStore.ON_DEVICE, SessionStore.ON_DEVICE, name, email))
    }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(.22f), MaterialTheme.colorScheme.background), radius = 1200f)).imePadding(), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(24.dp).widthIn(max = 440.dp).verticalScroll(rememberScrollState()), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(.96f))) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.padding(12.dp), tint = Color.White) }
                Text("BYAK AI", fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("Bring Your API Key. Bring Your Intelligence.", color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (!useServer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp))
                        Text("Runs on your phone. Your chats and API keys stay on this device.", style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = {
                        if (!GoogleSignIn.configured) error = "Google Sign-In isn't enabled in this build."
                        else attempt { GoogleSignIn.signIn(context.findActivity() ?: context)?.let { account -> app.local.setIdentity(account.name, account.email); app.sessionStore.save(Session(SessionStore.ON_DEVICE, SessionStore.ON_DEVICE, account.name, account.email)) } }
                    }, Modifier.fillMaxWidth().height(52.dp), enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else { Icon(Icons.Outlined.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text("Continue with Google") }
                    }
                    OutlinedButton(onClick = { startOnDevice("You", "") }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Continue without an account") }
                    TextButton(onClick = { useServer = true; error = null }, Modifier.align(Alignment.CenterHorizontally)) { Icon(Icons.Outlined.Dns, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("I have a BYAK server account") }
                }
                AnimatedVisibility(useServer) { ServerSignIn(app, onBack = { useServer = false }) }
            }
        }
    }
}

/** Email/password (or Google) sign-in against a self-hosted BYAK server. */
@Composable private fun ServerSignIn(app: ByakApplication, onBack: () -> Unit) {
    val api = app.api; val settings = app.settings
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { editingServer = true }) { Icon(Icons.Outlined.Dns, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Server: ${server.removePrefix("https://").removePrefix("http://").ifBlank { "not set" }}", style = MaterialTheme.typography.labelMedium) }
        if (register) OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = email.isNotBlank() && !emailValid, supportingText = { if (email.isNotBlank() && !emailValid) Text("Enter a valid email") })
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showPassword) "Hide password" else "Show password") } },
            supportingText = { if (register) Text("At least 10 characters") })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { attempt { if (register) api.register(name.trim(), email.trim(), password) else api.login(email.trim(), password) } }, Modifier.fillMaxWidth().height(52.dp), enabled = canSubmit) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (register) "Create server account" else "Sign in to server")
        }
        OutlinedButton(onClick = { attempt { GoogleSignIn.signIn(context.findActivity() ?: context)?.let { api.google(it.idToken) } } }, Modifier.fillMaxWidth(), enabled = !busy && GoogleSignIn.configured) { Icon(Icons.Outlined.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text("Google via server") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("← Use on this phone") }
            TextButton(onClick = { register = !register; error = null }) { Text(if (register) "Have an account?" else "Create account") }
        }
    }
    if (editingServer) ServerDialog(server, settings, onDismiss = { editingServer = false }) { editingServer = false }
}
