import com.google.protobuf.Timestamp
import com.squaredcandy.europa.model.SmartLight
import com.squaredcandy.europa.model.SmartLightCapability
import com.squaredcandy.europa.model.SmartLightData
import com.squaredcandy.protobuf.v1.model.*
import com.squaredcandy.protobuf.v1.provider.ProvideSmartLightRequest
import com.squaredcandy.protobuf.v1.provider.SmartLightProviderServiceProto
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.random.Random

const val PROVIDER_IP_ADDRESS = "10.10.10.10"

fun getTestSmartLightAllCapabilities(): SmartLight {
    return SmartLight(
        name = "Test Smart Light All Capabilities",
        macAddress = "12:34:56:78:90:10",
        smartLightData = listOf(
            getTestSmartLightData()
        )
    )
}
fun getTestSmartLightNoLocation(): SmartLight {
    return SmartLight(
        name = "Test Smart Light No Location",
        macAddress = "11:12:13:14:15:16",
        smartLightData = listOf(
            getTestSmartLightDataNoLocation()
        )
    )
}
fun getTestSmartLightNoColor(): SmartLight {
    return SmartLight(
        name = "Test Smart Light No Color",
        macAddress = "11:12:13:14:15:19",
        smartLightData = listOf(
            getTestSmartLightDataNoColor()
        )
    )
}
fun getTestSmartLightNoCapabilities(): SmartLight {
    return SmartLight(
        name = "Test Smart Light No Capabilities",
        macAddress = "11:12:13:14:15:43",
        smartLightData = listOf(
            getTestSmartLightDataNoCapabilities()
        )
    )
}

private var colorSelectionToggle = false
    get() {
        return field.apply {
            field = !field
        }
    }
fun getTestSmartLightData(): SmartLightData {
    return SmartLightData(
        ipAddress = "192.168.1.${Random.nextInt(0, 255)}",
        isOn = Random.nextBoolean(),
        capabilities = listOf(
            getSmartLightCapabilityColor(),
            SmartLightCapability.SmartLightLocation(
                "Home/Bedroom",
            ),
        )
    )
}
fun getTestSmartLightDataNoLocation(): SmartLightData {
    return SmartLightData(
        ipAddress = "192.168.1.${Random.nextInt(0, 255)}",
        isOn = Random.nextBoolean(),
        capabilities = listOf(
            getSmartLightCapabilityColor(),
        )
    )
}
fun getTestSmartLightDataNoColor(): SmartLightData {
    return SmartLightData(
        ipAddress = "192.168.1.${Random.nextInt(0, 255)}",
        isOn = Random.nextBoolean(),
        capabilities = listOf(
            SmartLightCapability.SmartLightLocation(
                "Home/Bedroom",
            ),
        )
    )
}
fun getTestSmartLightDataNoCapabilities(): SmartLightData {
    return SmartLightData(
        ipAddress = "192.168.1.${Random.nextInt(0, 255)}",
        isOn = Random.nextBoolean(),
        capabilities = listOf()
    )
}
fun getSmartLightCapabilityColor(): SmartLightCapability.SmartLightColor {
    return if(colorSelectionToggle){
        SmartLightCapability.SmartLightColor.SmartLightKelvin(
            kelvin = Random.nextInt(1000, 7500),
            brightness = Random.nextFloat(),
        )
    } else {
        SmartLightCapability.SmartLightColor.SmartLightHSB(
            hue = Random.nextFloat(),
            saturation = Random.nextFloat(),
            brightness = Random.nextFloat(),
        )
    }
}

fun SmartLightProto.LightColorProtoModel.toSmartLightColor(): SmartLightCapability.SmartLightColor {
    return when(colorCase) {
        SmartLightProto.LightColorProtoModel.ColorCase.HSB -> SmartLightCapability.SmartLightColor.SmartLightHSB(hsb.hue, hsb.saturation, hsb.brightness)
        SmartLightProto.LightColorProtoModel.ColorCase.KELVIN -> SmartLightCapability.SmartLightColor.SmartLightKelvin(kelvin.kelvin, kelvin.brightness)
        SmartLightProto.LightColorProtoModel.ColorCase.COLOR_NOT_SET, null -> throw StatusRuntimeException(Status.INVALID_ARGUMENT)
    }
}
fun SmartLightProto.LightLocationProtoModel.toSmartLightLocation(): SmartLightCapability.SmartLightLocation {
    return SmartLightCapability.SmartLightLocation(location)
}
fun SmartLight.toProvideSmartLightRequest(): SmartLightProviderServiceProto.ProvideSmartLightRequest {
    return ProvideSmartLightRequest {
        smartLight = toSmartLightProtoModel()
        providerIpAddress = PROVIDER_IP_ADDRESS
        receiveCommands = true
    }
}
fun SmartLight.toSmartLightProtoModel(): SmartLightProto.SmartLightProtoModel {
    return SmartLightProtoModel {
        name = this@toSmartLightProtoModel.name
        macAddress = this@toSmartLightProtoModel.macAddress
        created {
            seconds = this@toSmartLightProtoModel.created.toEpochSecond()
            nanos = this@toSmartLightProtoModel.created.nano
        }
        updated {
            seconds = this@toSmartLightProtoModel.lastUpdated.toEpochSecond()
            nanos = this@toSmartLightProtoModel.lastUpdated.nano
        }
        this@toSmartLightProtoModel.smartLightData.forEach { smartLightData ->
            addData {
                timestamp {
                    seconds = smartLightData.timestamp.toEpochSecond()
                    nanos = smartLightData.timestamp.nano
                }
                isConnected = smartLightData.isConnected
                if(smartLightData.ipAddress != null) {
                    ipAddress = smartLightData.ipAddress
                }
                isOn = smartLightData.isOn
                smartLightData.capabilities.forEach { smartLightCapability ->
                    when(smartLightCapability) {
                        is SmartLightCapability.SmartLightColor.SmartLightHSB -> {
                            color {
                                hsb {
                                    hue = smartLightCapability.hue
                                    saturation = smartLightCapability.saturation
                                    brightness = smartLightCapability.brightness
                                }
                            }
                        }
                        is SmartLightCapability.SmartLightColor.SmartLightKelvin -> {
                            color {
                                kelvin {
                                    kelvin = smartLightCapability.kelvin
                                    brightness = smartLightCapability.brightness
                                }
                            }
                        }
                        is SmartLightCapability.SmartLightLocation -> {
                            location {
                                location = smartLightCapability.location
                            }
                        }
                    }
                }
            }
        }
    }
}
fun SmartLightCapability.SmartLightColor.toLightColorProtoModel(): SmartLightProto.LightColorProtoModel {
    return LightColorProtoModel {
        when(this@toLightColorProtoModel) {
            is SmartLightCapability.SmartLightColor.SmartLightHSB -> {
                hsb {
                    hue = this@toLightColorProtoModel.hue
                    saturation = this@toLightColorProtoModel.saturation
                    brightness = this@toLightColorProtoModel.brightness
                }
            }
            is SmartLightCapability.SmartLightColor.SmartLightKelvin -> {
                kelvin {
                    kelvin = this@toLightColorProtoModel.kelvin
                    brightness = this@toLightColorProtoModel.brightness
                }
            }
        }
    }
}
fun SmartLightProto.SmartLightProtoModel.toSmartLight(offset: ZoneOffset = OffsetDateTime.now().offset): SmartLight {
    return SmartLight(
        name = this.name,
        macAddress = this.macAddress,
        created = this.created.toOffsetDateTime(offset),
        lastUpdated = this.updated.toOffsetDateTime(offset),
        smartLightData = this.dataList.map { data -> data.toSmartLightData(offset) }
    )
}
fun Timestamp.toOffsetDateTime(offset: ZoneOffset): OffsetDateTime {
    return Instant.ofEpochSecond(seconds, nanos.toLong()).atOffset(offset)
}
fun SmartLightProto.SmartLightDataProtoModel.toSmartLightData(offset: ZoneOffset): SmartLightData {
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
fun SmartLightProto.SmartLightDataProtoModel.getColorCapability(): SmartLightCapability.SmartLightColor? {
    return when (color?.colorCase) {
        SmartLightProto.LightColorProtoModel.ColorCase.HSB -> {
            SmartLightCapability.SmartLightColor.SmartLightHSB(
                color.hsb.hue,
                color.hsb.saturation,
                color.hsb.brightness,
            )
        }
        SmartLightProto.LightColorProtoModel.ColorCase.KELVIN -> {
            SmartLightCapability.SmartLightColor.SmartLightKelvin(
                color.kelvin.kelvin,
                color.kelvin.brightness,
            )
        }
        SmartLightProto.LightColorProtoModel.ColorCase.COLOR_NOT_SET, null -> null
    }
}
fun SmartLightProto.SmartLightDataProtoModel.getLocationCapability(): SmartLightCapability.SmartLightLocation? {
    return if(hasLocation()) {
        SmartLightCapability.SmartLightLocation(location.location)
    } else null
}