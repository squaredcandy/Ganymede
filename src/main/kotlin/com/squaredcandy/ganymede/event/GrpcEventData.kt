package com.squaredcandy.ganymede.event

data class GrpcEventData(
    val serviceName: String,
    val methodName: String,
    val eventDataMap: MutableMap<String, Any> = mutableMapOf(),
)