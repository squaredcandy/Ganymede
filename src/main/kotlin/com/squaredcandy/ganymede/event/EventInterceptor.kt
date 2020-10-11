package com.squaredcandy.ganymede.event

import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

internal class EventInterceptor(
    serviceList: List<EventHandlingService>
) : ServerInterceptor, EventFlow {

    private val eventEmitter = Channel<GrpcEventData>(0)
    override val eventFlow: Flow<GrpcEventData> = eventEmitter.receiveAsFlow()
    /**
     * A map which holds our services which allow us to call them to customise the [GrpcEventData]
     */
    private val serviceMap: Map<String, EventHandlingService> = generateServiceMap(serviceList)

    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val serviceName = call.methodDescriptor.serviceName
        val eventData = if(serviceName != null) {
            val methodName = call.methodDescriptor.fullMethodName.substringAfter("/")
            val initialData = generateInitialEventData(serviceName, methodName, headers)
            serviceMap[serviceName]?.customiseEventData(initialData, headers)
        } else {
            println("gRPC called where the service name was null")
            null
        }
        if(eventData != null) {
            // Call event handler
            eventEmitter.offer(eventData)
        }

        return next.startCall(call, headers)
    }

    /**
     * Generate an initial [GrpcEventData] object
     *
     * @param serviceName The gRPC calls' service name
     * @param methodName The gRPC calls' method name
     * @param metadata The gRPC calls' metadata
     * @return A [GrpcEventData] object ready for customising
     */
    private fun generateInitialEventData(
        serviceName: String,
        methodName: String,
        metadata: Metadata,
    ): GrpcEventData {
        return GrpcEventData(serviceName, methodName).apply {
            val ipAddress = metadata[IP_ADDRESS_METADATA_KEY]
            val macAddress = metadata[MAC_ADDRESS_METADATA_KEY]
            if(ipAddress != null) {
                eventDataMap[IP_ADDRESS_KEY] = ipAddress
            }
            if(macAddress != null) {
                eventDataMap[MAC_ADDRESS_KEY] = macAddress
            }
        }
    }

    /**
     * Generate a service map so we can log these services when the service name matches the ones we generate here
     *
     * @param serviceList The list of services we want to handle events for
     * @return A map which
     */
    private fun generateServiceMap(serviceList: List<EventHandlingService>): Map<String, EventHandlingService> {
        return serviceList.associateBy { service ->
            service.bindService().serviceDescriptor.name
        }
    }

    companion object {
        val IP_ADDRESS_METADATA_KEY: Metadata.Key<String> = Metadata.Key.of("REQUEST_IP_ADDRESS", Metadata.ASCII_STRING_MARSHALLER)
        val MAC_ADDRESS_METADATA_KEY: Metadata.Key<String> = Metadata.Key.of("REQUEST_MAC_ADDRESS", Metadata.ASCII_STRING_MARSHALLER)

        const val IP_ADDRESS_KEY = "IPAddress"
        const val MAC_ADDRESS_KEY = "MACAddress"
    }
}