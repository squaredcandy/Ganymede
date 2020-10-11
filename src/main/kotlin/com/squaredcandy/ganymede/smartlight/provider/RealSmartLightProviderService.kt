package com.squaredcandy.ganymede.smartlight.provider

import com.squaredcandy.ganymede.event.EventHandlingService
import com.google.protobuf.Timestamp
import com.squaredcandy.europa.model.SmartLight
import com.squaredcandy.europa.model.SmartLightCapability
import com.squaredcandy.europa.model.SmartLightData
import com.squaredcandy.europa.util.isSuccess
import com.squaredcandy.protobuf.v1.model.*
import com.squaredcandy.protobuf.v1.model.SmartLightProto.*
import com.squaredcandy.protobuf.v1.provider.*
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import com.squaredcandy.ganymede.smartlight.model.SmartLightCommand
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal class RealSmartLightProviderService(
    private val providerService: SmartLightProviderService
) : SmartLightProviderServiceCoroutineGrpc.SmartLightProviderServiceImplBase(), EventHandlingService {

    override suspend fun provideSmartLight(
        request: SmartLightProviderServiceProto.ProvideSmartLightRequest
    ): SmartLightProviderServiceProto.ProvideSmartLightResponse {
        return ProvideSmartLightResponse {
            updated = providerService.provideSmartLight(request.smartLight.toSmartLight()).isSuccess()
        }
    }

    override suspend fun openSmartLightCommandStream(
        request: SmartLightProviderServiceProto.SmartLightCommandRequest,
        responseChannel: SendChannel<SmartLightProviderServiceProto.ServerSmartLightCommand>
    ) {
        val ipAddress = request.ipAddress
        if(ipAddress == null || ipAddress.isBlank()) {
            responseChannel.close(StatusRuntimeException(Status.INVALID_ARGUMENT))
            return
        }
        val relayChannel = Channel<SmartLightCommand>(0)
        // Open a stream to the outside world, keeping the gRPC details hidden
        providerService.openCommandStream(ipAddress, relayChannel)

        relayChannel.consumeAsFlow()
            .catch { throwable ->
                responseChannel.close(throwable)
                relayChannel.close(throwable)
            }
            .cancellable()
            .collect {
                responseChannel.send(it.toServerSmartLightCommand())
            }
    }

    private fun SmartLightProtoModel.toSmartLight(
        offset: ZoneOffset = OffsetDateTime.now().offset
    ): SmartLight {
        return SmartLight(
            name = this.name,
            macAddress = this.macAddress,
            created = this.created.toOffsetDateTime(offset),
            lastUpdated = this.updated.toOffsetDateTime(offset),
            smartLightData = this.dataList.map { data -> data.toSmartLightData(offset) }
        )
    }

    private fun Timestamp.toOffsetDateTime(offset: ZoneOffset): OffsetDateTime {
        return Instant.ofEpochSecond(seconds, nanos.toLong()).atOffset(offset)
    }

    private fun SmartLightDataProtoModel.toSmartLightData(offset: ZoneOffset): SmartLightData {
        val capabilityList = mutableListOf(
            getColorCapability(),
            getLocationCapability(),
        ).filterNotNull()
        return SmartLightData(
            timestamp = timestamp.toOffsetDateTime(offset),
            ipAddress = ipAddress,
            isOn = isOn,
            capabilities = capabilityList
        )
    }

    private fun SmartLightDataProtoModel.getColorCapability(): SmartLightCapability.SmartLightColor? {
        return when (color?.colorCase) {
            LightColorProtoModel.ColorCase.HSB -> {
                SmartLightCapability.SmartLightColor.SmartLightHSB(
                    color.hsb.hue,
                    color.hsb.saturation,
                    color.hsb.brightness,
                )
            }
            LightColorProtoModel.ColorCase.KELVIN -> {
                SmartLightCapability.SmartLightColor.SmartLightKelvin(
                    color.kelvin.kelvin,
                    color.kelvin.brightness,
                )
            }
            LightColorProtoModel.ColorCase.COLOR_NOT_SET, null -> null
        }
    }

    private fun SmartLightDataProtoModel.getLocationCapability(): SmartLightCapability.SmartLightLocation? {
        return if(hasLocation()) {
            SmartLightCapability.SmartLightLocation(location.location)
        } else null
    }

    private fun SmartLightCommand.toServerSmartLightCommand(): SmartLightProviderServiceProto.ServerSmartLightCommand {
        return when(this) {
            is SmartLightCommand.UpdateName -> toUpdateNameRequest()
            is SmartLightCommand.UpdatePower -> toUpdatePowerRequest()
            is SmartLightCommand.UpdateColor -> toUpdateColorRequest()
            is SmartLightCommand.UpdateLocation -> toUpdateLocationRequest()
        }
    }

    private fun SmartLightCommand.UpdateName.toUpdateNameRequest(): SmartLightProviderServiceProto.ServerSmartLightCommand {
        return ServerSmartLightCommand {
            setProperty {
                name = SetSmartLightName {
                    this.newName = this@toUpdateNameRequest.name
                }
            }
        }
    }

    private fun SmartLightCommand.UpdatePower.toUpdatePowerRequest(): SmartLightProviderServiceProto.ServerSmartLightCommand {
        return ServerSmartLightCommand {
            setProperty {
                power = SetSmartLightPower {
                    this.isOn = this@toUpdatePowerRequest.isOn
                }
            }
        }
    }

    private fun SmartLightCommand.UpdateColor.toUpdateColorRequest(): SmartLightProviderServiceProto.ServerSmartLightCommand {
        return ServerSmartLightCommand {
            setProperty {
                color = SetSmartLightColor {
                    color {
                        when(this@toUpdateColorRequest.color) {
                            is SmartLightCapability.SmartLightColor.SmartLightHSB -> {
                                hsb {
                                    this.hue = this@toUpdateColorRequest.color.hue
                                    this.brightness = this@toUpdateColorRequest.color.brightness
                                    this.saturation = this@toUpdateColorRequest.color.saturation
                                }
                            }
                            is SmartLightCapability.SmartLightColor.SmartLightKelvin -> {
                                kelvin {
                                    this.kelvin = this@toUpdateColorRequest.color.kelvin
                                    this.brightness = this@toUpdateColorRequest.color.brightness
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun SmartLightCommand.UpdateLocation.toUpdateLocationRequest(): SmartLightProviderServiceProto.ServerSmartLightCommand {
        return ServerSmartLightCommand {
            setProperty {
                location {
                    location {
                        location = this@toUpdateLocationRequest.location.location
                    }
                }
            }
        }
    }
}