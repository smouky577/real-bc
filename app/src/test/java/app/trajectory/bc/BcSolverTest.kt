package app.trajectory.bc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BcSolverTest {
    @Test
    fun recoversBcFromOneDistance() {
        val ratio = BcSolver.densityRatio(15.0, 0.0)
        val expected = 0.034
        val speed = BcSolver.speedAt(250.0, 40.0, expected, ratio)
        val fit = BcSolver.fit(listOf(Shot(40.0, speed, 250.0)), ratio)
        assertEquals(expected, fit.bc, 0.0015)
        assertEquals(speed, fit.points[0].predictedMps, 0.4)
    }

    @Test
    fun recoversBcFromTwoDistances() {
        val ratio = BcSolver.densityRatio(22.0, 400.0)
        val expected = 0.11
        val shots = listOf(25.0, 60.0).map { d ->
            Shot(d, BcSolver.speedAt(320.0, d, expected, ratio), 320.0)
        }
        val fit = BcSolver.fit(shots, ratio)
        assertEquals(expected, fit.bc, 0.002)
        assertTrue(fit.meanAbsErrorMps < 0.5)
    }

    @Test
    fun higherBcKeepsMoreSpeed() {
        val ratio = 1.0
        val low = BcSolver.speedAt(280.0, 50.0, 0.02, ratio)
        val high = BcSolver.speedAt(280.0, 50.0, 0.12, ratio)
        assertTrue(high > low)
        assertTrue(low < 280.0)
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
