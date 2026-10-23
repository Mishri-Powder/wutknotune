package link.roadio.riff.ui

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import link.roadio.riff.player.core.settings.MotionRestriction

/** Observe the actual AAOS UX state; loss of evidence immediately restores UNKNOWN. */
internal class AaosUxRestrictionMonitor(
    context: Context,
    private val onChange: (MotionRestriction) -> Unit,
) : AutoCloseable {
    private var manager: CarUxRestrictionsManager? = null
    private var car: Car? = null
    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener(::observe)
    private var closed = false

    init {
        car =
            Car.createCar(
                context,
                Handler(Looper.getMainLooper()),
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                Car.CarServiceLifecycleListener { connectedCar, ready ->
                    if (closed) return@CarServiceLifecycleListener
                    manager?.let { runCatching { it.unregisterListener() } }
                    manager = null
                    onChange(MotionRestriction.UNKNOWN)
                    if (ready) {
                        runCatching {
                            connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                        }.onSuccess { connectedManager ->
                            manager = connectedManager
                            runCatching {
                                connectedManager.registerListener(listener)
                                observe(connectedManager.currentCarUxRestrictions)
                            }.onFailure { onChange(MotionRestriction.UNKNOWN) }
                        }
                    }
                },
            )
    }

    private fun observe(restrictions: CarUxRestrictions?) {
        onChange(
            when (restrictions?.isRequiresDistractionOptimization) {
                true -> MotionRestriction.RESTRICTED
                false -> MotionRestriction.UNRESTRICTED
                null -> MotionRestriction.UNKNOWN
            },
        )
    }

    override fun close() {
        closed = true
        manager?.let { runCatching { it.unregisterListener() } }
        manager = null
        car?.disconnect()
        car = null
    }
}
