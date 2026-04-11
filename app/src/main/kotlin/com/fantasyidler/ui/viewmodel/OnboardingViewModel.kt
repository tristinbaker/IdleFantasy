package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.repository.GlobalStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repo: GlobalStateRepository,
) : ViewModel() {

    /** null = still loading from DB, true = show onboarding, false = already complete */
    private val _showOnboarding = MutableStateFlow<Boolean?>(null)
    val showOnboarding: StateFlow<Boolean?> = _showOnboarding

    init {
        viewModelScope.launch {
            _showOnboarding.value = !repo.isOnboardingComplete()
        }
    }

    fun complete() {
        viewModelScope.launch {
            repo.markOnboardingComplete()
            _showOnboarding.value = false
        }
    }

    fun reopen() {
        viewModelScope.launch {
            repo.clearOnboardingComplete()
            _showOnboarding.value = true
        }
    }
}
