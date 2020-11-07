package com.squaredcandy.ganymede.smartlight

import com.squaredcandy.io.db.smartlight.SmartLightDatabase
import com.squaredcandy.europa.model.SmartLight
import com.squaredcandy.europa.util.Result
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import com.squaredcandy.ganymede.smartlight.model.SmartLightCommand
import com.squaredcandy.ganymede.smartlight.model.SmartLightUpdateRequest
import com.squaredcandy.ganymede.smartlight.provider.SmartLightProviderService
import com.squaredcandy.ganymede.smartlight.user.SmartLightUserService
import com.squaredcandy.io.db.util.ChangeType
import com.squaredcandy.io.db.util.DatabaseErrorType
import com.squaredcandy.io.db.util.DatabaseException
import kotlinx.coroutines.ExperimentalCoroutinesApi

open class SmartLightServiceLink(
    private val database: SmartLightDatabase,
): SmartLightUserService, SmartLightProviderService {

    /**
     * Mapping Light IP Addresses to Provider IP Addresses
     */
    private val lightProviderMap = mutableMapOf<String, String>()

    /**
     * Mapping Provider IP Addresses to their channels
     */
    private val commandMap = mutableMapOf<String, SendChannel<SmartLightCommand>>()

    override suspend fun getSmartLight(macAddress: String): Result<SmartLight> {
        return database.getSmartLight(macAddress)
    }

    override suspend fun getSmartLightUpdates(macAddress: String): Flow<ChangeType<SmartLight>> {
        return database.getOnSmartLightChanged(macAddress)
    }

    @ExperimentalCoroutinesApi
    override suspend fun userSetSmartLight(request: SmartLightUpdateRequest): Result<Unit> {
        val smartLight = when(val getSmartLightResult = database.getSmartLight(request.macAddress)) {
            is Result.Success -> getSmartLightResult.value
            is Result.Failure -> {
                return if(getSmartLightResult.throwable is DatabaseException) {
                    when((getSmartLightResult.throwable as DatabaseException).type) {
                        DatabaseErrorType.NOT_FOUND -> Result.Failure(StatusRuntimeException(Status.NOT_FOUND))
                        // It should not return anything other than not found
                        else -> Result.Failure(StatusRuntimeException(Status.INTERNAL))
                    }
                } else {
                    getSmartLightResult
                }
            }
        }

        val lightIpAddress = smartLight.smartLightData.lastOrNull()?.ipAddress
            ?: return Result.Failure(StatusRuntimeException(Status.NOT_FOUND))
        val providerIpAddress = lightProviderMap[lightIpAddress]
            ?: return Result.Failure(StatusRuntimeException(Status.UNAVAILABLE))
        val commandChannel = commandMap[providerIpAddress]
            ?: return Result.Failure(StatusRuntimeException(Status.UNAVAILABLE))
        if(commandChannel.isClosedForSend) {
            commandMap.remove(lightIpAddress)
            return Result.Failure(StatusRuntimeException(Status.UNAVAILABLE))
        }
        val command = when(request) {
            is SmartLightUpdateRequest.Name -> request.toUpdateName(lightIpAddress)
            is SmartLightUpdateRequest.Power -> request.toUpdatePower(lightIpAddress)
            is SmartLightUpdateRequest.Color -> request.toUpdateColor(lightIpAddress)
            is SmartLightUpdateRequest.Location -> request.toUpdateLocation(lightIpAddress)
        }

        commandChannel.send(command)
        return Result.Success(Unit)
    }

    override suspend fun provideSmartLight(smartLight: SmartLight, newProviderIpAddress: String?): Result<Unit> {
        return database.upsertSmartLight(smartLight).also {
            if(newProviderIpAddress == null) return@also
            val ipAddress = smartLight.smartLightData.lastOrNull()?.ipAddress ?: return@also
            lightProviderMap[ipAddress] = newProviderIpAddress
        }
    }

    @ExperimentalCoroutinesApi
    final override fun openCommandStream(
        providerIpAddress: String,
        commandChannel: SendChannel<SmartLightCommand>
    ) {
        val currentChannel = commandMap[providerIpAddress]
        if(currentChannel != null && !currentChannel.isClosedForSend) {
            commandChannel.close(StatusRuntimeException(Status.ALREADY_EXISTS))
            return
        }
        commandMap[providerIpAddress] = commandChannel

        commandChannel.invokeOnClose {
            commandMap.remove(providerIpAddress)
        }
    }

    private fun SmartLightUpdateRequest.Name.toUpdateName(ipAddress: String): SmartLightCommand.UpdateName {
        return SmartLightCommand.UpdateName(
            ipAddress = ipAddress,
            macAddress = this.macAddress,
            name = this.name,
        )
    }

    private fun SmartLightUpdateRequest.Power.toUpdatePower(ipAddress: String): SmartLightCommand.UpdatePower {
        return SmartLightCommand.UpdatePower(
            ipAddress = ipAddress,
            macAddress = this.macAddress,
            isOn = this.isOn,
        )
    }

    private fun SmartLightUpdateRequest.Color.toUpdateColor(ipAddress: String): SmartLightCommand.UpdateColor {
        return SmartLightCommand.UpdateColor(
            ipAddress = ipAddress,
            macAddress = this.macAddress,
            color = this.color,
        )
    }

    private fun SmartLightUpdateRequest.Location.toUpdateLocation(ipAddress: String): SmartLightCommand.UpdateLocation {
        return SmartLightCommand.UpdateLocation(
            ipAddress = ipAddress,
            macAddress = this.macAddress,
            location = this.Location,
        )
    }
}