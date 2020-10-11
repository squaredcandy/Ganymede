package com.squaredcandy.ganymede.event

import io.grpc.BindableService
import io.grpc.Metadata

internal interface EventHandlingService: BindableService {
    fun customiseEventData(
        currentEventData: GrpcEventData,
        metadata: Metadata,
    ) = currentEventData
}