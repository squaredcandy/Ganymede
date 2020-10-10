import com.squaredcandy.europa.model.SmartLight
import com.squaredcandy.europa.model.SmartLightCapability
import com.squaredcandy.europa.model.SmartLightData
import kotlin.random.Random

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