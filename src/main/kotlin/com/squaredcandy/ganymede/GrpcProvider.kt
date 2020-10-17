package com.squaredcandy.ganymede

import com.squaredcandy.ganymede.event.EventHandlingService
import com.squaredcandy.ganymede.event.EventInterceptor
import com.squaredcandy.ganymede.event.EventService
import com.squaredcandy.ganymede.smartlight.SmartLightServiceLink
import com.squaredcandy.ganymede.smartlight.provider.RealSmartLightProviderService
import com.squaredcandy.ganymede.smartlight.user.RealSmartLightUserService
import io.grpc.ServerBuilder

object GrpcProvider {
    private fun getSmartLightServices(serviceLink: SmartLightServiceLink): List<EventHandlingService> = listOf(
        RealSmartLightUserService(serviceLink),
        RealSmartLightProviderService(serviceLink),
    )

    fun <T : ServerBuilder<T>?> ServerBuilder<T>.addSmartLightService(
        smartLightServiceLink: SmartLightServiceLink,
        eventService: EventService? = null,
    ): ServerBuilder<T> {
        val serviceList = getSmartLightServices(smartLightServiceLink)
        val eventInterceptor = EventInterceptor(serviceList)
        eventService?.init(eventInterceptor)

        serviceList.forEach { addService(it) }
        intercept(eventInterceptor)
        return this
    }
}