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

    private fun cdmShots(air: Air, bc: Double, slope: Double, center: Double, noise: List<Double> = emptyList()): List<Shot> {
        val shots = ArrayList<Shot>()
        var i = 0
        for (v0 in listOf(285.0, 245.0)) for (d in listOf(50.0, 100.0)) {
            val v = BcSolver.speedAt(v0, d, bc, air, DragModel.G1, slope, center)
            shots.add(Shot(d, v + noise.getOrElse(i++) { 0.0 }, v0))
        }
        return shots
    }

    @Test
    fun cdmRecoversBcThatChangesWithSpeed() {
        val air = Air.of(15.0, 0.0)
        val shots = cdmShots(air, bc = 0.10, slope = 2.0, center = 0.75)
        val cdm = BcSolver.fitCdm(shots, air)!!
        for (mach in listOf(cdm.machMin, cdm.machCenter, cdm.machMax)) {
            val truth = BcSolver.bcAtMach(0.10, 2.0, 0.75, mach)
            val fitted = BcSolver.bcAtMach(cdm.bc, cdm.slope, cdm.machCenter, mach)
            assertEquals("mach $mach", truth, fitted, truth * 0.01)
        }
        assertTrue(cdm.significant)
        assertTrue(cdm.meanAbsErrorMps < 0.05)
        assertTrue(cdm.points.first().mach < cdm.points.last().mach)
    }

    @Test
    fun cdmWithChronoNoiseStaysClose() {
        val air = Air.of(20.0, 300.0)
        val shots = cdmShots(air, 0.10, 2.0, 0.75, noise = listOf(0.3, -0.3, -0.3, 0.3))
        val cdm = BcSolver.fitCdm(shots, air)!!
        for (mach in listOf(cdm.machMin, cdm.machMax)) {
            val truth = BcSolver.bcAtMach(0.10, 2.0, 0.75, mach)
            val fitted = BcSolver.bcAtMach(cdm.bc, cdm.slope, cdm.machCenter, mach)
            assertEquals("mach $mach", truth, fitted, truth * 0.06)
        }
    }

    @Test
    fun cdmOnConstantBcIsNotSignificant() {
        val air = Air.of(15.0, 0.0)
        val cdm = BcSolver.fitCdm(cdmShots(air, 0.10, 0.0, 0.75), air)!!
        assertEquals(0.0, cdm.changePct, 0.5)
        assertTrue(!cdm.significant)
    }

    @Test
    fun cdmNeedsTwoIntervals() {
        val air = Air.of(15.0, 0.0)
        val v = BcSolver.speedAt(280.0, 50.0, 0.1, air)
        assertEquals(null, BcSolver.fitCdm(listOf(Shot(50.0, v, 280.0), Shot(50.0, v + 0.2, 280.0)), air))
    }

    @Test
    fun cdmFileRoundTrip() {
        val cdm = listOf(CdmPoint(0.70, 0.105), CdmPoint(0.75, 0.100), CdmPoint(0.80, 0.0952))
        val load = LoadFile.Load("S", 3.5, 280.0, 0.1, 15.0, 0.0, listOf(Shot(50.0, 260.0, 280.0)), cdm)
        val text = LoadFile.parse(LoadFile.machineBlock(load))!!
        assertEquals(3, text.cdm?.size)
        assertEquals(0.0952, text.cdm!![2].bc, 1e-6)
        val json = LoadFile.parse(LoadFile.json(load))!!
        assertEquals(3, json.cdm?.size)
        assertEquals(0.75, json.cdm!![1].mach, 1e-6)
        assertEquals(1, json.shots.size)
        assertEquals(null, LoadFile.parse(LoadFile.machineBlock(load.copy(cdm = null)))!!.cdm)
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
