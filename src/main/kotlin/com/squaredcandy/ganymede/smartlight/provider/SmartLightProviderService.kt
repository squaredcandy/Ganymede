package com.squaredcandy.ganymede.smartlight.provider

import com.squaredcandy.europa.model.SmartLight
import com.squaredcandy.europa.util.Result
import kotlinx.coroutines.channels.SendChannel
import com.squaredcandy.ganymede.smartlight.model.SmartLightCommand

interface SmartLightProviderService {
    suspend fun provideSmartLight(smartLight: SmartLight): Result<Unit>
    fun openCommandStream(ipAddress: String, commandChannel: SendChannel<SmartLightCommand>)
}