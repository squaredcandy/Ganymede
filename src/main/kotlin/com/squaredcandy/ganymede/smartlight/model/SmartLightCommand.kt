package com.squaredcandy.ganymede.smartlight.model

import com.squaredcandy.europa.model.SmartLightCapability

sealed class SmartLightCommand(
    open val ipAddress: String,
    open val macAddress: String,
) {
    data class UpdateName(
        override val ipAddress: String,
        override val macAddress: String,
        val name: String,
    ): SmartLightCommand(
        ipAddress,
        macAddress,
    )

    data class UpdatePower(
        override val ipAddress: String,
        override val macAddress: String,
        val isOn: Boolean,
    ): SmartLightCommand(
        ipAddress,
        macAddress,
    )

    data class UpdateColor(
        override val ipAddress: String,
        override val macAddress: String,
        val color: SmartLightCapability.SmartLightColor,
    ): SmartLightCommand(
        ipAddress,
        macAddress,
    )

    data class UpdateLocation(
        override val ipAddress: String,
        override val macAddress: String,
        val location: SmartLightCapability.SmartLightLocation,
    ): SmartLightCommand(
        ipAddress,
        macAddress,
    )
}