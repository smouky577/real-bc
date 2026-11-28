package app.trajectory.bc

/**
 * Text block Ballistic Scope imports. Human text may sit above the marker.
 * A JSON object with the same fields is still read (older exports); the app writes only text.
 */
object LoadFile {
    const val MARKER = "ballisticscope-load 1"

    data class Load(
        val name: String,
        val weightG: Double?,
        val v0: Double,
        val bc: Double,
        val temperatureC: Double,
        val altitudeM: Double,
        val shots: List<Shot>,
        /** CDM: equivalent G1 BC by Mach. Null when the shots cannot carry a curve. */
        val cdm: List<CdmPoint>? = null,
    )

    fun machineBlock(load: Load): String = buildString {
        appendLine(MARKER)
        appendLine("drag_model=${BcSolver.MODEL}")
        appendLine("name=${load.name.replace("\n", " ").trim()}")
        if (load.weightG != null) appendLine("weight_g=${fmt(load.weightG)}")
        appendLine("v0_mps=${fmt(load.v0)}")
        appendLine("bc=${fmt(load.bc)}")
        appendLine("temperature_c=${fmt(load.temperatureC)}")
        appendLine("altitude_m=${fmt(load.altitudeM)}")
        for (shot in load.shots) {
            appendLine("shot=${fmt(shot.v0)} ${fmt(shot.distanceM)} ${fmt(shot.speedMps)}")
        }
        // CDM: "mach g1_bc". Older Ballistic Scope ignores these lines and keeps using bc.
        load.cdm?.forEach { appendLine("cdm=${fmt3(it.mach)} ${fmt(it.bc)}") }
    }

    fun json(load: Load): String = buildString {
        appendLine("{")
        appendLine("  \"format\": \"ballisticscope-load\",")
        appendLine("  \"version\": 1,")
        appendLine("  \"dragModel\": \"${BcSolver.MODEL}\",")
        appendLine("  \"name\": ${jsonString(load.name)},")
        if (load.weightG != null) appendLine("  \"weightGrams\": ${fmt(load.weightG)},")
        appendLine("  \"muzzleVelocityMps\": ${fmt(load.v0)},")
        appendLine("  \"ballisticCoefficient\": ${fmt(load.bc)},")
        appendLine("  \"temperatureCelsius\": ${fmt(load.temperatureC)},")
        appendLine("  \"altitudeMeters\": ${fmt(load.altitudeM)},")
        val cdm = load.cdm
        if (cdm != null) {
            appendLine("  \"cdm\": [")
            cdm.forEachIndexed { i, p ->
                val comma = if (i == cdm.lastIndex) "" else ","
                appendLine("    {\"mach\": ${fmt3(p.mach)}, \"bc\": ${fmt(p.bc)}}$comma")
            }
            appendLine("  ],")
        }
        appendLine("  \"shots\": [")
        load.shots.forEachIndexed { i, shot ->
            val comma = if (i == load.shots.lastIndex) "" else ","
            appendLine("    {\"v0Mps\": ${fmt(shot.v0)}, \"distanceMeters\": ${fmt(shot.distanceM)}, \"speedMps\": ${fmt(shot.speedMps)}}$comma")
        }
        appendLine("  ]")
        append("}")
    }

    fun parse(text: String): Load? {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) return parseJson(trimmed)
        val marker = trimmed.indexOf(MARKER)
        if (marker < 0) return null
        return parseLines(trimmed.substring(marker).lineSequence())
    }

    private fun parseLines(lines: Sequence<String>): Load? {
        var name = ""
        var weight: Double? = null
        var v0: Double? = null
        var bc: Double? = null
        var temp = 15.0
        var alt = 0.0
        val rawShots = ArrayList<List<Double>>()
        val cdm = ArrayList<CdmPoint>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line == MARKER) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            when (key) {
                "name" -> name = value
                "weight_g" -> weight = num(value)
                "v0_mps" -> v0 = num(value)
                "bc" -> bc = num(value)
                "temperature_c" -> temp = num(value) ?: temp
                "altitude_m" -> alt = num(value) ?: alt
                "shot" -> {
                    val nums = value.split(Regex("\\s+")).mapNotNull(::num)
                    if (nums.size >= 2) rawShots.add(nums)
                }
                "cdm" -> {
                    val nums = value.split(Regex("\\s+")).mapNotNull(::num)
                    if (nums.size >= 2) cdm.add(CdmPoint(nums[0], nums[1]))
                }
            }
        }
        val shots = rawShots.mapNotNull { nums ->
            when {
                nums.size >= 3 -> Shot(nums[1], nums[2], nums[0])
                v0 != null -> Shot(nums[0], nums[1], v0)
                else -> null
            }
        }
        val muzzle = v0 ?: shots.map { it.v0 }.average().takeIf { shots.isNotEmpty() } ?: return null
        val coefficient = bc ?: return null
        return Load(name.ifBlank { "Снаряд" }, weight, muzzle, coefficient, temp, alt, shots, cdm.takeIf { it.size >= 2 })
    }

    private fun parseJson(text: String): Load? {
        fun field(key: String): String? {
            val quoted = Regex(""""$key"\s*:\s*"([^"\\]*)"""").find(text)?.groupValues?.get(1)
            if (quoted != null) return quoted
            return Regex(""""$key"\s*:\s*(-?\d+(?:\.\d+)?)""").find(text)?.groupValues?.get(1)
        }
        val v0 = field("muzzleVelocityMps")?.let(::num) ?: return null
        val bc = field("ballisticCoefficient")?.let(::num) ?: return null
        val weight = field("weightGrams")?.let(::num)
        val temp = field("temperatureCelsius")?.let(::num) ?: 15.0
        val alt = field("altitudeMeters")?.let(::num) ?: 0.0
        val name = field("name") ?: "Снаряд"
        val shots = ArrayList<Shot>()
        val section = Regex(""""shots"\s*:\s*\[(.*)]""", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.get(1).orEmpty()
        for (obj in Regex("""\{[^{}]*\}""").findAll(section)) {
            val body = obj.value
            val distance = jsonNum(body, "distanceMeters") ?: continue
            val speed = jsonNum(body, "speedMps") ?: continue
            val shotV0 = jsonNum(body, "v0Mps") ?: v0
            shots.add(Shot(distance, speed, shotV0))
        }
        val cdmSection = Regex(""""cdm"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.get(1).orEmpty()
        val cdm = Regex("""\{[^{}]*\}""").findAll(cdmSection).mapNotNull { obj ->
            val mach = jsonNum(obj.value, "mach") ?: return@mapNotNull null
            val pointBc = jsonNum(obj.value, "bc") ?: return@mapNotNull null
            CdmPoint(mach, pointBc)
        }.toList()
        return Load(name.ifBlank { "Снаряд" }, weight, v0, bc, temp, alt, shots, cdm.takeIf { it.size >= 2 })
    }

    private fun jsonNum(body: String, key: String): Double? =
        Regex(""""$key"\s*:\s*(-?\d+(?:\.\d+)?)""").find(body)?.groupValues?.get(1)?.let(::num)

    private fun num(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()

    private fun fmt3(v: Double): String = "%.3f".format(java.util.Locale.US, v)

    private fun fmt(v: Double): String = "%.6f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')

    private fun jsonString(value: String): String {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
        return "\"$escaped\""
    }
}
