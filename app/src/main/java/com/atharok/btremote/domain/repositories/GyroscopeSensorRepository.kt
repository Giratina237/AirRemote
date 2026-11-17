package com.atharok.btremote.domain.repositories

import kotlinx.coroutines.flow.StateFlow

interface GyroscopeSensorRepository {

    fun startListening()

    fun stopListening()

    fun getDisplayRotation(): Int

    fun setOnSample(onSample: ((Float, Float, Float, Long) -> Unit)?)

    fun setOnOrientation(onOrientation: ((Float, Float, Long) -> Unit)?)

    fun getPositions(): StateFlow<Triple<Float, Float, Float>>
}