package com.squaredcandy.ganymede.smartlight.user

import com.squaredcandy.ganymede.event.EventHandlingService
import com.google.protobuf.Timestamp
import com.squaredcandy.io.db.util.ChangeType
import com.squaredcandy.europa.model.SmartLight
import com.squaredcandy.europa.model.SmartLightCapability.*
import com.squaredcandy.europa.model.SmartLightData
import com.squaredcandy.europa.util.Result
import com.squaredcandy.europa.util.isSuccess
import com.squaredcandy.europa.util.onSuccessSuspended
import com.squaredcandy.protobuf.v1.model.*
import com.squaredcandy.protobuf.v1.model.SmartLightProto.*
import com.squaredcandy.protobuf.v1.user.GetSmartLightResponse
import com.squaredcandy.protobuf.v1.user.SetSmartLightPropertyResponse
import com.squaredcandy.protobuf.v1.user.SmartLightUserServiceCoroutineGrpc
import com.squaredcandy.protobuf.v1.user.SmartLightUserServiceProto.*
import com.squaredcandy.protobuf.v1.user.SmartLightUserServiceProto.SetSmartLightPropertyRequest.*
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.catch
import com.squaredcandy.ganymede.smartlight.model.SmartLightUpdateRequest
import com.squaredcandy.protobuf.v1.user.GetAllSmartLightsResponse
import kotlinx.coroutines.flow.collect
import java.time.OffsetDateTime

internal class RealSmartLightUserService(
    private val userService: SmartLightUserService
) : SmartLightUserServiceCoroutineGrpc.SmartLightUserServiceImplBase(), EventHandlingService {

    override suspend fun getSmartLight(request: GetSmartLightRequest): GetSmartLightResponse {
        val mac: String = if(!request.macAddress.isNullOrBlank()) request.macAddress
        else throw StatusRuntimeException(Status.INVALID_ARGUMENT)
        return when(val getSmartLightResult = userService.getSmartLight(mac)) {
            is Result.Success -> getSmartLightResult.value.toLightStateResponse()
            is Result.Failure -> throw StatusRuntimeException(Status.NOT_FOUND)
        }
    }

    override suspend fun getSmartLightStream(
        request: GetSmartLightRequest,
        responseChannel: SendChannel<GetSmartLightResponse>
    ) {
        val mac = request.macAddress ?: run {
            responseChannel.close(StatusRuntimeException(Status.INVALID_ARGUMENT))
            return
        }

        // Give an update immediately if we have that smart light
        val getSmartLightResult = userService.getSmartLight(mac)
        getSmartLightResult.onSuccessSuspended { smartLight ->
            responseChannel.send(smartLight.toLightStateResponse())
        }

        // Collect a flow of events that happen to that mac address
        userService.getSmartLightUpdates(mac)
            .catch {
                responseChannel.close(StatusRuntimeException(Status.ABORTED))
            }
            .collect { changeType ->
                when(changeType) {
                    is ChangeType.Inserted -> {
                        responseChannel.send(changeType.item.toLightStateResponse())
                    }
                    is ChangeType.Updated -> {
                        responseChannel.send(changeType.item.toLightStateResponse())
                    }
                    ChangeType.Removed -> {
                    }
                }
            }
    }

    override suspend fun getAllSmartLights(request: GetAllSmartLightsRequest): GetAllSmartLightsResponse {
        return when(val getAllSmartLightsResult = userService.getAllSmartLights()) {
            is Result.Success -> getAllSmartLightsResult.value.toGetAllLightsResponse()
            is Result.Failure -> throw StatusRuntimeException(Status.INTERNAL)
        }
    }

    override suspend fun setSmartLightProperty(request: SetSmartLightPropertyRequest): SetSmartLightPropertyResponse {
        val macAddress = if(!request.macAddress.isNullOrBlank()) request.macAddress
        else throw StatusRuntimeException(Status.INVALID_ARGUMENT)
        val updateRequest = when(request.propertyCase) {
            PropertyCase.NAME -> SmartLightUpdateRequest.Name(macAddress, request.name.newName)
            PropertyCase.POWER -> SmartLightUpdateRequest.Power(macAddress, request.power.isOn)
            PropertyCase.COLOR -> SmartLightUpdateRequest.Color(macAddress, request.color.color.toSmartLightColor())
            PropertyCase.LOCATION -> SmartLightUpdateRequest.Location(macAddress, request.location.location.toSmartLightLocation())
            PropertyCase.PROPERTY_NOT_SET, null -> throw StatusRuntimeException(Status.INVALID_ARGUMENT)
        }

        return SetSmartLightPropertyResponse {
            this.updated = userService.userSetSmartLight(updateRequest).isSuccess()
        }
    }

    private fun SmartLight.toLightStateResponse(): GetSmartLightResponse {
        return GetSmartLightResponse {
            smartLight = this@toLightStateResponse.toSmartLightProtoModel()
        }
    }

    private fun List<SmartLight>.toGetAllLightsResponse(): GetAllSmartLightsResponse {
        return GetAllSmartLightsResponse {
            addAllSmartLight(
                this@toGetAllLightsResponse.map { it.toSmartLightProtoModel() }.toMutableList()
            )
        }
    }

    private fun SmartLight.toSmartLightProtoModel(): SmartLightProtoModel {
        return SmartLightProtoModel {
            name = this@toSmartLightProtoModel.name
            macAddress = this@toSmartLightProtoModel.macAddress
            created = this@toSmartLightProtoModel.created.toTimestamp()
            updated = this@toSmartLightProtoModel.lastUpdated.toTimestamp()
            addAllData(this@toSmartLightProtoModel.smartLightData.map { it.toSmartLightDataProtoModel() })
        }
    }

    private fun SmartLightData.toSmartLightDataProtoModel(): SmartLightDataProtoModel {
        return SmartLightDataProtoModel {
            timestamp = this@toSmartLightDataProtoModel.timestamp.toTimestamp()
            isConnected = this@toSmartLightDataProtoModel.isConnected
            ipAddress = this@toSmartLightDataProtoModel.ipAddress
            isOn = this@toSmartLightDataProtoModel.isOn
            capabilities.forEach { capabilities ->
                when (capabilities) {
                    is SmartLightColor.SmartLightHSB -> {
                        color = capabilities.toLightColorProtoModel()
                    }
                    is SmartLightColor.SmartLightKelvin -> {
                        color = capabilities.toLightColorProtoModel()
                    }
                    is SmartLightLocation -> {
                        location = capabilities.toLocationProtoModel()
                    }
                }
            }
        }
    }

    private fun SmartLightColor.SmartLightHSB.toLightColorProtoModel(): LightColorProtoModel {
        return LightColorProtoModel {
            hsb {
                hue = this@toLightColorProtoModel.hue
                saturation = this@toLightColorProtoModel.saturation
                brightness = this@toLightColorProtoModel.brightness
            }
        }
    }

    private fun SmartLightColor.SmartLightKelvin.toLightColorProtoModel(): LightColorProtoModel {
        return LightColorProtoModel {
            kelvin {
                kelvin = this@toLightColorProtoModel.kelvin
                brightness = this@toLightColorProtoModel.brightness
            }
        }
    }

    private fun SmartLightLocation.toLocationProtoModel(): LightLocationProtoModel {
        return LightLocationProtoModel {
            location = this@toLocationProtoModel.location
        }
    }

    private fun OffsetDateTime.toTimestamp(): Timestamp {
        return Timestamp.newBuilder()
            .setSeconds(toEpochSecond())
            .setNanos(nano)
            .build()
    }

    private fun LightColorProtoModel.toSmartLightColor(): SmartLightColor {
        return when(colorCase) {
            LightColorProtoModel.ColorCase.HSB -> SmartLightColor.SmartLightHSB(hsb.hue, hsb.saturation, hsb.brightness)
            LightColorProtoModel.ColorCase.KELVIN -> SmartLightColor.SmartLightKelvin(kelvin.kelvin, kelvin.brightness)
            LightColorProtoModel.ColorCase.COLOR_NOT_SET, null -> throw StatusRuntimeException(Status.INVALID_ARGUMENT)
        }
    }

    private fun LightLocationProtoModel.toSmartLightLocation(): SmartLightLocation {
        return SmartLightLocation(location)
    }
}