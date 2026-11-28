package app.trajectory.bc

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** Air for the drag law: density ratio to 1.225 kg/m³ and speed of sound. */
data class Air(val densityRatio: Double, val speedOfSound: Double) {
    companion object {
        fun of(tempC: Double, altitudeM: Double) = Air(
            densityRatio = BcSolver.densityRatio(tempC, altitudeM),
            speedOfSound = BcSolver.speedOfSound(tempC),
        )
    }
}

/**
 * Same drag law as Ballistic Scope (`com.ballisticscope.app.ballistics.BallisticEngine`):
 * McCoy G1/G7 table, speed of sound 331.3 + 0.606·t, RK4 with dt = 1 ms, BC in lb/in².
 * The fitted BC is the number that scope engine must use to reproduce these speeds.
 */
object BcSolver {
    const val MODEL = "ballisticscope-drag-v2"
    private const val G = 9.80665
    private const val SEA_LEVEL_DENSITY = 1.225
    private const val BC_KG_PER_M2 = 703.0696
    private const val DT = 0.001
    const val BC_MIN = 0.005
    const val BC_MAX = 1.0

    fun speedOfSound(tempC: Double): Double = 331.3 + 0.606 * tempC

    /** Pressure from altitude exactly as Ballistic Scope `airDensityFromAltitude`. */
    fun airDensity(tempC: Double, altitudeM: Double): Double {
        val tK = tempC + 273.15
        val pressurePa = 1013.25 * exp(-altitudeM / 8500.0) * 100.0
        return pressurePa / (287.05 * tK)
    }

    fun densityRatio(tempC: Double, altitudeM: Double): Double =
        airDensity(tempC, altitudeM) / SEA_LEVEL_DENSITY

    /**
     * CDM as an equivalent G1 BC that changes with Mach:
     * BC(M) = bc / (1 + slope·(M − machCenter)). slope = 0 is the plain G1 BC.
     */
    fun bcAtMach(bc: Double, slope: Double, machCenter: Double, mach: Double): Double =
        bc / (1.0 + slope * (mach - machCenter)).coerceAtLeast(0.2)

    /** Drag deceleration per unit speed, so a = −k·v_vec. */
    private fun dragK(
        speed: Double, bc: Double, air: Air, model: DragModel, slope: Double, machCenter: Double,
    ): Double {
        val mach = speed / air.speedOfSound
        val cd = DragTables.cd(model, mach)
        val bcHere = if (slope == 0.0) bc else bcAtMach(bc, slope, machCenter, mach)
        return (SEA_LEVEL_DENSITY * air.densityRatio * cd * PI) / (8.0 * bcHere * BC_KG_PER_M2) * speed
    }

    /** Speed after [distanceM] metres, barrel horizontal, no wind. Stops exactly at the distance. */
    fun speedAt(
        v0: Double, distanceM: Double, bc: Double, air: Air, model: DragModel = DragModel.G1,
        slope: Double = 0.0, machCenter: Double = 0.0,
    ): Double {
        var x = 0.0
        var vx = v0
        var vy = 0.0
        var steps = 0
        val maxSteps = 80_000
        fun k(vx: Double, vy: Double): Double =
            dragK(sqrt(vx * vx + vy * vy).coerceAtLeast(1e-6), bc, air, model, slope, machCenter)
        while (x < distanceM && steps < maxSteps && vx > 5.0) {
            val k1 = k(vx, vy)
            val a1x = -k1 * vx
            val a1y = -k1 * vy - G
            val v2x = vx + a1x * DT * 0.5
            val v2y = vy + a1y * DT * 0.5
            val k2 = k(v2x, v2y)
            val a2x = -k2 * v2x
            val a2y = -k2 * v2y - G
            val v3x = vx + a2x * DT * 0.5
            val v3y = vy + a2y * DT * 0.5
            val k3 = k(v3x, v3y)
            val a3x = -k3 * v3x
            val a3y = -k3 * v3y - G
            val v4x = vx + a3x * DT
            val v4y = vy + a3y * DT
            val k4 = k(v4x, v4y)
            val a4x = -k4 * v4x
            val a4y = -k4 * v4y - G
            val dvx = (a1x + 2 * a2x + 2 * a3x + a4x) / 6.0
            val dvy = (a1y + 2 * a2y + 2 * a3y + a4y) / 6.0
            val nx = x + (vx + 0.5 * dvx * DT) * DT
            val nvx = vx + dvx * DT
            val nvy = vy + dvy * DT
            if (nx >= distanceM) {
                // Last step: interpolate to the exact distance instead of overshooting.
                val f = ((distanceM - x) / (nx - x)).coerceIn(0.0, 1.0)
                vx += (nvx - vx) * f
                vy += (nvy - vy) * f
                break
            }
            x = nx
            vx = nvx
            vy = nvy
            steps++
        }
        return sqrt(vx * vx + vy * vy)
    }

    fun fit(shots: List<Shot>, air: Air, model: DragModel = DragModel.G1): Fit {
        require(shots.isNotEmpty())
        fun cost(bc: Double): Double {
            var sum = 0.0
            for (shot in shots) {
                val delta = speedAt(shot.v0, shot.distanceM, bc, air, model) - shot.speedMps
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
                predictedMps = speedAt(shot.v0, shot.distanceM, bc, air, model),
                ownBc = bcForShot(shot, air, model),
            )
        }
        val meanAbs = points.sumOf { kotlin.math.abs(it.predictedMps - it.shot.speedMps) } / points.size
        val atClamp = bc <= BC_MIN * 1.02 || bc >= BC_MAX * 0.98
        return Fit(bc = bc, points = points, meanAbsErrorMps = meanAbs, limited = atClamp && meanAbs > 1.0)
    }

    /**
     * Fits CDM (level + slope of BC over Mach) to all shots. Needs at least two different
     * speed intervals: two distances, or two V0. Returns null when the data cannot carry a slope.
     * [chronoSigmaMps] is the assumed chronograph error, used for the uncertainty.
     */
    fun fitCdm(shots: List<Shot>, air: Air, chronoSigmaMps: Double = 0.5): CdmFit? {
        val groups = shots.map { Math.round(it.v0 * 2) to Math.round(it.distanceM * 2) }.toSet()
        if (groups.size < 2) return null
        val sos = air.speedOfSound
        val machMin = shots.minOf { it.speedMps } / sos
        val machMax = shots.maxOf { it.v0 } / sos
        if (machMax - machMin < 0.02) return null
        val center = (machMin + machMax) / 2.0
        val base = fit(shots, air).bc

        fun cost(bc: Double, slope: Double): Double {
            var sum = 0.0
            for (s in shots) {
                val d = speedAt(s.v0, s.distanceM, bc, air, DragModel.G1, slope, center) - s.speedMps
                sum += d * d
            }
            return sum
        }

        fun bestBc(slope: Double): Pair<Double, Double> {
            var lo = (base / 1.6).coerceIn(BC_MIN, BC_MAX)
            var hi = (base * 1.6).coerceIn(BC_MIN, BC_MAX)
            repeat(30) {
                val m1 = lo + (hi - lo) / 3.0
                val m2 = hi - (hi - lo) / 3.0
                if (cost(m1, slope) <= cost(m2, slope)) hi = m2 else lo = m1
            }
            val bc = (lo + hi) / 2.0
            return bc to cost(bc, slope)
        }

        var bestSlope = 0.0
        var bestCost = bestBc(0.0).second
        var s = -SLOPE_MAX
        while (s <= SLOPE_MAX + 1e-9) {
            val c = bestBc(s).second
            if (c < bestCost) {
                bestCost = c
                bestSlope = s
            }
            s += 0.5
        }
        var lo = (bestSlope - 0.5).coerceAtLeast(-SLOPE_MAX)
        var hi = (bestSlope + 0.5).coerceAtMost(SLOPE_MAX)
        repeat(24) {
            val m1 = lo + (hi - lo) / 3.0
            val m2 = hi - (hi - lo) / 3.0
            if (bestBc(m1).second <= bestBc(m2).second) hi = m2 else lo = m1
        }
        val slope = (lo + hi) / 2.0
        val (bc, finalCost) = bestBc(slope)

        // Uncertainty of the slope from the Jacobian: cov = σ² (JᵀJ)⁻¹.
        val n = shots.size
        val rms = sqrt(finalCost / n)
        val sigma = if (n > 2) maxOf(chronoSigmaMps, rms * sqrt(n / (n - 2.0))) else chronoSigmaMps
        val hBc = bc * 1e-3
        val hS = 1e-3
        var a11 = 0.0
        var a12 = 0.0
        var a22 = 0.0
        for (shot in shots) {
            fun v(b: Double, sl: Double) = speedAt(shot.v0, shot.distanceM, b, air, DragModel.G1, sl, center)
            val jb = (v(bc + hBc, slope) - v(bc - hBc, slope)) / (2 * hBc)
            val js = (v(bc, slope + hS) - v(bc, slope - hS)) / (2 * hS)
            a11 += jb * jb
            a12 += jb * js
            a22 += js * js
        }
        val det = a11 * a22 - a12 * a12
        val slopeSd = if (det > 1e-12) sigma * sqrt(a11 / det) else Double.POSITIVE_INFINITY

        val span = machMax - machMin
        val bcSlow = bcAtMach(bc, slope, center, machMin)
        val bcFast = bcAtMach(bc, slope, center, machMax)
        val changePct = (bcSlow / bcFast - 1.0) * 100.0
        val changeSdPct = slopeSd * span * 100.0
        val points = ArrayList<CdmPoint>()
        val first = Math.ceil(machMin * 100.0) / 100.0
        points.add(CdmPoint(machMin, bcSlow))
        var m = first
        while (m < machMax - 1e-9) {
            if (m > machMin + 1e-9) points.add(CdmPoint(m, bcAtMach(bc, slope, center, m)))
            m += 0.01
        }
        points.add(CdmPoint(machMax, bcFast))
        return CdmFit(
            bc = bc, slope = slope, machCenter = center, machMin = machMin, machMax = machMax,
            speedMin = machMin * sos, speedMax = machMax * sos,
            bcSlow = bcSlow, bcFast = bcFast, changePct = changePct, changeSdPct = changeSdPct,
            meanAbsErrorMps = shots.sumOf {
                kotlin.math.abs(speedAt(it.v0, it.distanceM, bc, air, DragModel.G1, slope, center) - it.speedMps)
            } / n,
            shots = n, points = points,
        )
    }

    private const val SLOPE_MAX = 4.0

    private fun bcForShot(shot: Shot, air: Air, model: DragModel): Double {
        val slow = speedAt(shot.v0, shot.distanceM, BC_MIN, air, model)
        val fast = speedAt(shot.v0, shot.distanceM, BC_MAX, air, model)
        if (shot.speedMps >= fast) return BC_MAX
        if (shot.speedMps <= slow) return BC_MIN
        var lo = BC_MIN
        var hi = BC_MAX
        repeat(42) {
            val mid = (lo + hi) / 2.0
            val predicted = speedAt(shot.v0, shot.distanceM, mid, air, model)
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

/** One point of the CDM curve: equivalent G1 BC at this Mach. */
data class CdmPoint(val mach: Double, val bc: Double)

data class CdmFit(
    val bc: Double,
    val slope: Double,
    val machCenter: Double,
    val machMin: Double,
    val machMax: Double,
    val speedMin: Double,
    val speedMax: Double,
    val bcSlow: Double,
    val bcFast: Double,
    /** How much BC at the slow end differs from the fast end, %. */
    val changePct: Double,
    /** One standard deviation of [changePct] from chronograph noise. */
    val changeSdPct: Double,
    val meanAbsErrorMps: Double,
    val shots: Int,
    val points: List<CdmPoint>,
) {
    /** The change is larger than twice its noise. */
    val significant: Boolean get() = kotlin.math.abs(changePct) > 2.0 * changeSdPct
}
