package app.trajectory.bc

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Same drag law as Ballistic Scope ([com.ballisticscope.app.ballistics.BallisticEngine.dragAccel]).
 * The fitted BC is the number that scope engine must use to reproduce these speeds.
 */
object BcSolver {
    const val MODEL = "ballisticscope-drag-v1"
    private const val G = 9.80665
    private const val SEA_LEVEL_DENSITY = 1.225
    private const val DT = 0.0005
    const val BC_MIN = 0.005
    const val BC_MAX = 1.0

    fun airDensity(tempC: Double, altitudeM: Double): Double {
        val tK = tempC + 273.15
        val pressure = 101325.0 * exp(-altitudeM / 8500.0)
        return pressure / (287.05 * tK)
    }

    fun densityRatio(tempC: Double, altitudeM: Double): Double =
        airDensity(tempC, altitudeM) / SEA_LEVEL_DENSITY

    fun dragAccel(speedMps: Double, bc: Double, densityRatio: Double): Double {
        val mach = speedMps / 340.29
        val cd = when {
            mach < 0.5 -> 0.20 + 0.10 * mach
            mach < 0.8 -> 0.25 + 0.35 * (mach - 0.5)
            mach < 1.0 -> 0.355 + 1.2 * (mach - 0.8)
            else -> 0.595 + 0.4 * (mach - 1.0)
        }
        val k = (densityRatio * cd) / (bc * 12.0)
        return k * speedMps * speedMps * 0.001
    }

    /** Scalar speed after [distanceM] metres, barrel horizontal. */
    fun speedAt(v0: Double, distanceM: Double, bc: Double, densityRatio: Double): Double {
        var x = 0.0
        var vx = v0
        var vy = 0.0
        var steps = 0
        val maxSteps = 400_000
        while (x < distanceM && steps < maxSteps && vx > 5.0) {
            val speed = hypot(vx, vy)
            val drag = dragAccel(speed, bc, densityRatio)
            val ax = -drag * (vx / speed)
            val ay = -G - drag * (vy / speed)
            val remain = distanceM - x
            var step = DT
            val nvx = vx + ax * step
            val dx = nvx * step
            if (dx > remain && dx > 0.0) step = DT * (remain / dx)
            vx += ax * step
            vy += ay * step
            x += vx * step
            steps++
        }
        return hypot(vx, vy)
    }

    fun fit(shots: List<Shot>, densityRatio: Double): Fit {
        require(shots.isNotEmpty())
        fun cost(bc: Double): Double {
            var sum = 0.0
            for (shot in shots) {
                val delta = speedAt(shot.v0, shot.distanceM, bc, densityRatio) - shot.speedMps
                sum += delta * delta
            }
            return sum
        }

        var best = 0.03
        var bestCost = Double.POSITIVE_INFINITY
        val samples = 72
        for (i in 0..samples) {
            val t = i / samples.toDouble()
            val bc = BC_MIN * (BC_MAX / BC_MIN).pow(t)
            val c = cost(bc)
            if (c < bestCost) {
                bestCost = c
                best = bc
            }
        }
        var lo = (best / 1.35).coerceIn(BC_MIN, BC_MAX)
        var hi = (best * 1.35).coerceIn(BC_MIN, BC_MAX)
        if (hi < lo) hi = lo
        repeat(36) {
            val m1 = lo + (hi - lo) / 3.0
            val m2 = hi - (hi - lo) / 3.0
            if (cost(m1) <= cost(m2)) hi = m2 else lo = m1
        }
        val bc = ((lo + hi) / 2.0).coerceIn(BC_MIN, BC_MAX)
        val points = shots.map { shot ->
            PointFit(
                shot = shot,
                predictedMps = speedAt(shot.v0, shot.distanceM, bc, densityRatio),
                ownBc = bcForShot(shot, densityRatio),
            )
        }
        val meanAbs = points.sumOf { kotlin.math.abs(it.predictedMps - it.shot.speedMps) } / points.size
        val atClamp = bc <= BC_MIN * 1.02 || bc >= BC_MAX * 0.98
        return Fit(bc = bc, points = points, meanAbsErrorMps = meanAbs, limited = atClamp && meanAbs > 1.0)
    }

    private fun bcForShot(shot: Shot, densityRatio: Double): Double {
        val slow = speedAt(shot.v0, shot.distanceM, BC_MIN, densityRatio)
        val fast = speedAt(shot.v0, shot.distanceM, BC_MAX, densityRatio)
        if (shot.speedMps >= fast) return BC_MAX
        if (shot.speedMps <= slow) return BC_MIN
        var lo = BC_MIN
        var hi = BC_MAX
        repeat(42) {
            val mid = (lo + hi) / 2.0
            val predicted = speedAt(shot.v0, shot.distanceM, mid, densityRatio)
            if (predicted > shot.speedMps) hi = mid else lo = mid
        }
        return (lo + hi) / 2.0
    }
}

data class Shot(val distanceM: Double, val speedMps: Double, val v0: Double)

data class PointFit(val shot: Shot, val predictedMps: Double, val ownBc: Double)

data class Fit(
    val bc: Double,
    val points: List<PointFit>,
    val meanAbsErrorMps: Double,
    val limited: Boolean,
)
