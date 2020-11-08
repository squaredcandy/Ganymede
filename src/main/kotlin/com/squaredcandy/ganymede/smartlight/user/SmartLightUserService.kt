package com.squaredcandy.ganymede.smartlight.user

import com.squaredcandy.io.db.util.ChangeType
import com.squaredcandy.europa.model.SmartLight
import com.squaredcandy.europa.util.Result
import kotlinx.coroutines.flow.Flow
import com.squaredcandy.ganymede.smartlight.model.SmartLightUpdateRequest

interface SmartLightUserService {
    suspend fun getSmartLight(macAddress: String): Result<SmartLight>
    suspend fun getSmartLightUpdates(macAddress: String): Flow<ChangeType<SmartLight>>
    suspend fun getAllSmartLights(): Result<List<SmartLight>>
    suspend fun userSetSmartLight(request: SmartLightUpdateRequest): Result<Unit>
}