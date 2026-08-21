package ai.byak.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.byak.app.ui.components.ByakLogo
import ai.byak.app.ui.theme.GlassCard
import ai.byak.app.ui.theme.NeonPurple

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var cloudVisible by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect -> if (effect is AuthEffect.Error) snackbar.showSnackbar(effect.message) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent) { padding ->
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF17101F), Color(0xFF0B0C10), Color(0xFF071014))),
            ).padding(padding).statusBarsPadding().navigationBarsPadding(),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(18.dp))
                ByakLogo(104.dp)
                Text("BYAK AI", fontSize = 31.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(
                    "A calm place to think, build, and get things done.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.height(30.dp))

                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.secondary)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("Private on this phone", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("No server needed. Your keys stay encrypted here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("What should BYAK call you?") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                        )
                        Button(
                            onClick = { viewModel.continuePrivately(name) },
                            enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            if (state.loading && !cloudVisible) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                            else Text("Continue privately")
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text("  or  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.weight(1f))
                }

                OutlinedButton(
                    onClick = { cloudVisible = !cloudVisible },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Outlined.Cloud, null)
                    Text(if (cloudVisible) " Hide cloud sign in" else " Sign in for sync & subscriptions")
                }

                AnimatedVisibility(cloudVisible) {
                    GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (state.registerMode) "Create your BYAK account" else "Welcome back", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            if (state.registerMode) OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                            )
                            Button(
                                onClick = { viewModel.submit(name, email, password) },
                                enabled = !state.loading && email.isNotBlank() && password.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                            ) { Text(if (state.registerMode) "Create account" else "Sign in") }
                            TextButton(onClick = { viewModel.setRegisterMode(!state.registerMode) }, Modifier.align(Alignment.CenterHorizontally)) {
                                Text(if (state.registerMode) "Already have an account? Sign in" else "New to BYAK? Create an account")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("Private by design · Bring your own AI key", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}
