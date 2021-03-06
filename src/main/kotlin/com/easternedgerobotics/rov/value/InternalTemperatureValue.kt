package com.easternedgerobotics.rov.value

data class InternalTemperatureValue(override val temperature: Float = 0f) : TemperatureValue {
    constructor() : this(0f)
}
