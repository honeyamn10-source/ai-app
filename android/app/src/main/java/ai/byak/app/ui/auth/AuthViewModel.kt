package ai.byak.app.ui.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.byak.app.domain.model.Session
import ai.byak.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class AuthUiState(
    val initialized: Boolean = false,
    val session: Session? = null,
    val loading: Boolean = false,
    val registerMode: Boolean = false,
)

sealed interface AuthEffect {
    data class Error(val message: String) : AuthEffect
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()
    private val mutableEffects = MutableSharedFlow<AuthEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<AuthEffect> = mutableEffects.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                mutableState.update { it.copy(initialized = true, session = session, loading = false) }
            }
        }
    }

    fun setRegisterMode(enabled: Boolean) = mutableState.update { it.copy(registerMode = enabled) }

    fun submit(name: String, email: String, password: String) {
        if (mutableState.value.loading) return
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = if (mutableState.value.registerMode) repository.register(name, email, password)
            else repository.signIn(email, password)
            result.onFailure { mutableEffects.emit(AuthEffect.Error(it.message ?: "Could not sign in")) }
            mutableState.update { it.copy(loading = false) }
        }
    }

    fun continuePrivately(name: String) {
        if (mutableState.value.loading) return
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            repository.continueOnDevice(name)
                .onFailure { mutableEffects.emit(AuthEffect.Error(it.message ?: "Could not create the private session")) }
            mutableState.update { it.copy(loading = false) }
        }
    }

    fun signOut() = viewModelScope.launch { repository.signOut() }
}
