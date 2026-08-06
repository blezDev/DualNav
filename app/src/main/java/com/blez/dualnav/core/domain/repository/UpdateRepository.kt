package com.blez.dualnav.core.domain.repository

import com.blez.dualnav.core.domain.model.AppUpdate
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.Result

interface UpdateRepository {
    /** Returns null when the latest GitHub release is not newer than the running app. */
    suspend fun checkForUpdate(): Result<AppUpdate?, DataError>
}
