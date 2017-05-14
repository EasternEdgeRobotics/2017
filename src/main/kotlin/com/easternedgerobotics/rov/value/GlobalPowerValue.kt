package com.easternedgerobotics.rov.value

data class GlobalPowerValue(override val speed: Float = 0f) : SpeedValue {
    constructor() : this(0f)
}
