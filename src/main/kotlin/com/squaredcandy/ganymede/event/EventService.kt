package com.squaredcandy.ganymede.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class EventService(
) {
    private var job: Job? = null

    fun close() {
        job?.cancel()
    }

    fun init(eventFlow: EventFlow) {
        job = CoroutineScope(Dispatchers.Default).launch {
            eventFlow.eventFlow.collect { event ->
                println(event)
                // add to database
            }
        }
    }
}