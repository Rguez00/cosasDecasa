package org.example.project.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.example.project.domain.model.AlertRule
import org.example.project.presentation.state.AlertsState

interface AlertsRepository {
    val alertsState: StateFlow<AlertsState>

    suspend fun addRule(rule: AlertRule)
    suspend fun removeRule(rule: AlertRule)
    suspend fun clearTriggered()
}
