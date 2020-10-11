package com.squaredcandy.ganymede.event

import kotlinx.coroutines.flow.Flow

interface EventFlow {
    val eventFlow: Flow<GrpcEventData>
}
