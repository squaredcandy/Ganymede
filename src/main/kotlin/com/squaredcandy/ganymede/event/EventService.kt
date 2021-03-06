package com.squaredcandy.ganymede.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class EventService {
    private var jobList: MutableList<Job> = mutableListOf()

    fun close() {
        jobList.forEach { it.cancel() }
    }

    fun init(eventFlow: EventFlow) {
        val newJob = CoroutineScope(Dispatchers.Default).launch {
            eventFlow.eventFlow.collect { event ->
                println(event)
                // add to database
            }
        }
        jobList.add(newJob)
    }
}