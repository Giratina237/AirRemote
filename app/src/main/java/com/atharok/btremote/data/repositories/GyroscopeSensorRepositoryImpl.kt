package com.atharok.btremote.data.repositories

import com.atharok.btremote.data.sensor.GyroscopeSensor
import com.atharok.btremote.domain.repositories.GyroscopeSensorRepository
import kotlinx.coroutines.flow.StateFlow

class GyroscopeSensorRepositoryImpl(private val sensor: GyroscopeSensor): GyroscopeSensorRepository {

    override fun startListening() {
        sensor.startListening()
    }

    override fun stopListening() {
        sensor.stopListening()
    }

    override fun getDisplayRotation(): Int = sensor.getDisplayRotation()

    override fun setOnSample(onSample: ((Float, Float, Float, Long) -> Unit)?) {
        sensor.onSample = onSample
    }

    override fun setOnOrientation(onOrientation: ((Float, Float, Long) -> Unit)?) {
        sensor.onOrientation = onOrientation
    }

    override fun getPositions(): StateFlow<Triple<Float, Float, Float>> = sensor.gyroscopePositionsState
}