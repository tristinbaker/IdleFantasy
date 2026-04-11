package com.fantasyidler.repository

import com.fantasyidler.data.db.dao.GlobalStateDao
import com.fantasyidler.data.model.GlobalState
import com.fantasyidler.data.model.GlobalStateKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalStateRepository @Inject constructor(
    private val dao: GlobalStateDao,
) {
    suspend fun isOnboardingComplete(): Boolean =
        dao.getValue(GlobalStateKey.ONBOARDING_COMPLETE) == "true"

    suspend fun markOnboardingComplete() {
        dao.setValue(GlobalState(
            key       = GlobalStateKey.ONBOARDING_COMPLETE,
            value     = "true",
            updatedAt = System.currentTimeMillis(),
        ))
    }

    suspend fun clearOnboardingComplete() {
        dao.delete(GlobalStateKey.ONBOARDING_COMPLETE)
    }
}
