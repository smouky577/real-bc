package app.trajectory.bc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BcSolverTest {
    @Test
    fun recoversBcFromOneDistance() {
        val air = Air.of(15.0, 0.0)
        val expected = 0.034
        val speed = BcSolver.speedAt(250.0, 40.0, expected, air)
        val fit = BcSolver.fit(listOf(Shot(40.0, speed, 250.0)), air)
        assertEquals(expected, fit.bc, 0.0015)
        assertEquals(speed, fit.points[0].predictedMps, 0.4)
    }

    @Test
    fun recoversBcFromTwoDistances() {
        val air = Air.of(22.0, 400.0)
        val expected = 0.11
        val shots = listOf(25.0, 60.0).map { d ->
            Shot(d, BcSolver.speedAt(320.0, d, expected, air), 320.0)
        }
        val fit = BcSolver.fit(shots, air)
        assertEquals(expected, fit.bc, 0.002)
        assertTrue(fit.meanAbsErrorMps < 0.5)
    }

    @Test
    fun higherBcKeepsMoreSpeed() {
        val air = Air.of(15.0, 0.0)
        val low = BcSolver.speedAt(280.0, 50.0, 0.02, air)
        val high = BcSolver.speedAt(280.0, 50.0, 0.12, air)
        assertTrue(high > low)
        assertTrue(low < 280.0)
    }

    /**
     * Reference speeds from Ballistic Scope `BallisticEngine.solve(...).impactVelocityMps`
     * (weather on, pressure = 1013.25·exp(−alt/8500), zero 50 m, scope 51 mm), run on 2026-11-28.
     * Ballistic Scope stops at the first 1 ms step past the distance, so its speed lies between
     * ours at d and ours one step (v·1 ms) further. 0.02 m/s slack covers the zeroing angle.
     */
    @Test
    fun matchesBallisticScopeAt50And100m() {
        data class Ref(val v0: Double, val bc: Double, val model: DragModel, val t: Double, val alt: Double, val d: Double, val v: Double)
        val refs = listOf(
            Ref(280.0, 0.115, DragModel.G1, 15.0, 0.0, 50.0, 259.8041),
            Ref(280.0, 0.115, DragModel.G1, 15.0, 0.0, 100.0, 242.8321),
            Ref(250.0, 0.034, DragModel.G1, 25.0, 500.0, 50.0, 206.0399),
            Ref(250.0, 0.034, DragModel.G1, 25.0, 500.0, 100.0, 171.0887),
            Ref(300.0, 0.060, DragModel.G7, 5.0, 200.0, 50.0, 278.9614),
            Ref(300.0, 0.060, DragModel.G7, 5.0, 200.0, 100.0, 260.0069),
        )
        for (r in refs) {
            val air = Air.of(r.t, r.alt)
            val atD = BcSolver.speedAt(r.v0, r.d, r.bc, air, r.model)
            val oneStepLater = BcSolver.speedAt(r.v0, r.d + atD * 0.001, r.bc, air, r.model)
            val msg = "$r: ours ${"%.4f".format(atD)} … ${"%.4f".format(oneStepLater)}"
            assertTrue(msg, r.v <= atD + 0.02)
            assertTrue(msg, r.v >= oneStepLater - 0.02)
        }
    }

    @Test
    fun fileRoundTrip() {
        val load = LoadFile.Load(
            name = "JSB 18",
            weightG = 1.175,
            v0 = 275.4,
            bc = 0.03125,
            temperatureC = 18.0,
            altitudeM = 120.0,
            shots = listOf(Shot(30.0, 240.1, 275.4), Shot(50.0, 210.5, 271.0)),
        )
        val parsed = LoadFile.parse("бележка\n" + LoadFile.machineBlock(load))
        assertEquals(load.name, parsed?.name)
        assertEquals(load.bc, parsed?.bc ?: 0.0, 1e-6)
        assertEquals(load.v0, parsed?.v0 ?: 0.0, 1e-6)
        assertEquals(2, parsed?.shots?.size)
        val fromJson = LoadFile.parse(LoadFile.json(load))
        assertEquals(load.bc, fromJson?.bc ?: 0.0, 1e-6)
        assertEquals(50.0, fromJson?.shots?.get(1)?.distanceM ?: 0.0, 1e-6)
        assertEquals(271.0, fromJson?.shots?.get(1)?.v0 ?: 0.0, 1e-6)
    }
}
