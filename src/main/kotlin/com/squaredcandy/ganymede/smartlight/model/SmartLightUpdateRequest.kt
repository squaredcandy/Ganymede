package com.squaredcandy.ganymede.smartlight.model

import com.squaredcandy.europa.model.SmartLightCapability

sealed class SmartLightUpdateRequest(open val macAddress: String) {
    data class Name(override val macAddress: String, val name: String): SmartLightUpdateRequest(macAddress)
    data class Power(override val macAddress: String, val isOn: Boolean): SmartLightUpdateRequest(macAddress)
    data class Color(override val macAddress: String, val color: SmartLightCapability.SmartLightColor): SmartLightUpdateRequest(macAddress)
    data class Location(override val macAddress: String, val Location: SmartLightCapability.SmartLightLocation): SmartLightUpdateRequest(macAddress)
}