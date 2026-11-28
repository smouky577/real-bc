package app.trajectory.bc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.trajectory.bc.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFS = "real_bc"
private const val KEY_WEIGHT = "weight"
private const val KEY_TEMP = "temperature"
private const val KEY_ALT = "altitude"
private const val KEY_BG = "bulgarian"
private const val REVOLUT = "revolut.me/georgi09qc"
private val BTC = listOf(
    "bc1qr84akpfry7sjs6759wwq722fyert9apcarpst6",
    "bc1p6hp5q20u9t9upsjt7425wgqdrs0cr8wnnlf9atu24z92pzsnq85q62uc8e",
)

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private class ShotRow(
        val root: View,
        val muzzle: EditText,
        val distance: EditText,
        val speed: EditText,
    )

    private val rows = ArrayList<ShotRow>()
    private var generation = 0
    private var lastLoad: LoadFile.Load? = null
    private var lastFit: Fit? = null
    private var bulgarian = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.isFocusableInTouchMode = true
        addRow()
        addRow()
        binding.sameV0.setOnCheckedChangeListener { _, _ -> applyV0Mode() }
        applyV0Mode()
        binding.credit.setOnClickListener { showAbout() }
        bulgarian = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_BG, true)
        binding.languageFlag.bulgarian = bulgarian
        binding.languageFlag.setOnClickListener {
            bulgarian = !bulgarian
            binding.languageFlag.bulgarian = bulgarian
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_BG, bulgarian).apply()
            applyLanguage()
        }
        applyLanguage()
        binding.addShot.isFocusable = false
        binding.calculate.isFocusable = false
        binding.export.isFocusable = false
        binding.addShot.setOnClickListener { addRow() }
        binding.calculate.setOnClickListener { calculate() }
        binding.export.setOnClickListener { export() }
        restoreMemory()
    }

    override fun onPause() {
        saveMemory()
        super.onPause()
    }

    private fun restoreMemory() {
        val memory = getSharedPreferences(PREFS, MODE_PRIVATE)
        binding.weightInput.setText(memory.getString(KEY_WEIGHT, "").orEmpty())
        binding.tempInput.setText(memory.getString(KEY_TEMP, "15").orEmpty().ifBlank { "15" })
        binding.altInput.setText(memory.getString(KEY_ALT, "0").orEmpty().ifBlank { "0" })
    }

    private fun saveMemory() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_WEIGHT, binding.weightInput.text?.toString()?.trim().orEmpty())
            .putString(KEY_TEMP, binding.tempInput.text?.toString()?.trim().orEmpty().ifBlank { "15" })
            .putString(KEY_ALT, binding.altInput.text?.toString()?.trim().orEmpty().ifBlank { "0" })
            .apply()
    }

    private fun showAbout() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val gap = (12 * resources.displayMetrics.density).toInt()
        val ink = ContextCompat.getColor(this, R.color.ink)
        val accent = ContextCompat.getColor(this, R.color.accent)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, gap, pad, 0)
        }
        fun plain(text: String, top: Int = 0, bold: Boolean = false) = TextView(this).apply {
            this.text = text
            setTextColor(ink)
            textSize = 15f
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, top, 0, 0)
        }
        fun copyLine(value: String) = TextView(this).apply {
            text = value
            setTextColor(accent)
            textSize = 15f
            setPadding(0, gap / 2, 0, gap / 2)
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("address", value))
                Toast.makeText(this@MainActivity, R.string.copied, Toast.LENGTH_SHORT).show()
            }
        }
        column.addView(plain(getString(R.string.about_donate_en)))
        column.addView(plain(getString(R.string.about_donate_bg), gap))
        column.addView(plain(getString(R.string.about_revolut), gap * 2, bold = true))
        column.addView(copyLine(REVOLUT))
        column.addView(plain(getString(R.string.about_btc), gap, bold = true))
        BTC.forEach { column.addView(copyLine(it)) }
        AlertDialog.Builder(this)
            .setTitle(R.string.about)
            .setView(ScrollView(this).apply { addView(column) })
            .setPositiveButton("OK", null)
            .show()
    }

    private fun t(bg: String, en: String) = if (bulgarian) bg else en

    private fun applyLanguage() {
        binding.nameInput.hint = t("Име на снаряда", "Projectile name")
        binding.weightInput.hint = t("Маса, грамове", "Mass, grams")
        binding.v0Input.hint = t("V0 от хронограф, м/с", "V0 from the chronograph, m/s")
        binding.sameV0.text = t("Едно V0", "One V0")
        binding.shotsTitle.text = t("Заснети изстрели", "Recorded shots")
        binding.tempLabel.text = t("Температура, °C", "Temperature, °C")
        binding.altLabel.text = t("Надморска височина, м", "Altitude, m")
        binding.addShot.text = t("Добави дистанция", "Add distance")
        binding.calculate.text = t("Сметни BC", "Calculate BC")
        binding.export.text = t("Експорт", "Export")
        for (row in rows) {
            row.distance.hint = t("Дистанция, м", "Distance, m")
            row.speed.hint = t("Скорост, м/с", "Speed, m/s")
            row.root.findViewById<TextView>(R.id.removeShot).text = t("Махни", "Remove")
        }
        val fit = lastFit
        val load = lastLoad
        if (fit != null && load != null) binding.result.text = describe(load, fit)
    }

    private fun applyV0Mode() {
        val shared = binding.sameV0.isChecked
        binding.v0Input.visibility = if (shared) View.VISIBLE else View.GONE
        for (row in rows) {
            row.muzzle.visibility = if (shared) View.GONE else View.VISIBLE
        }
        wireEnter()
    }

    private fun wireEnter() {
        val fields = ArrayList<EditText>()
        fields.add(binding.nameInput)
        fields.add(binding.weightInput)
        if (binding.v0Input.visibility == View.VISIBLE) fields.add(binding.v0Input)
        for (row in rows) {
            if (row.muzzle.visibility == View.VISIBLE) fields.add(row.muzzle)
            fields.add(row.distance)
            fields.add(row.speed)
        }
        fields.add(binding.tempInput)
        fields.add(binding.altInput)
        fields.forEachIndexed { index, field ->
            val next = fields.getOrNull(index + 1)
            var moved = false
            field.imeOptions = if (next == null) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
            field.setOnEditorActionListener { view, actionId, event ->
                val enter = event != null &&
                    (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                if (enter && event.action == KeyEvent.ACTION_DOWN) {
                    moved = false
                    return@setOnEditorActionListener true
                }
                if (moved) return@setOnEditorActionListener true
                val advance = enter || event == null ||
                    actionId == EditorInfo.IME_ACTION_NEXT ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_UNSPECIFIED ||
                    actionId == EditorInfo.IME_NULL
                if (!advance) return@setOnEditorActionListener false
                moved = true
                if (next == null) hideTyping(view) else next.requestFocus()
                true
            }
        }
    }

    private fun hideTyping(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        val root = binding.root
        root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        root.requestFocus()
        root.post { root.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS }
    }

    private fun addRow(distance: String = "", speed: String = "") {
        val row = layoutInflater.inflate(R.layout.item_shot, binding.shots, false)
        row.findViewById<EditText>(R.id.distanceInput).setText(distance)
        row.findViewById<EditText>(R.id.speedInput).setText(speed)
        row.findViewById<View>(R.id.v0Input).visibility = if (binding.sameV0.isChecked) View.GONE else View.VISIBLE
        val shot = ShotRow(
            row,
            row.findViewById(R.id.v0Input),
            row.findViewById(R.id.distanceInput),
            row.findViewById(R.id.speedInput),
        )
        row.findViewById<View>(R.id.removeShot).apply {
            isFocusable = false
            setOnClickListener {
                if (rows.size <= 1) return@setOnClickListener
                binding.shots.removeView(shot.root)
                rows.remove(shot)
                wireEnter()
            }
        }
        rows.add(shot)
        binding.shots.addView(row)
        wireEnter()
    }

    private fun calculate() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty().ifBlank { t("Снаряд", "Projectile") }
        val weight = number(binding.weightInput.text?.toString())
        val shared = binding.sameV0.isChecked
        val sharedV0 = if (shared) number(binding.v0Input.text?.toString()) else null
        val temp = number(binding.tempInput.text?.toString()) ?: 15.0
        val alt = number(binding.altInput.text?.toString()) ?: 0.0
        if (shared && (sharedV0 == null || sharedV0 < 30.0 || sharedV0 > 1500.0)) {
            show(t("Въведете едно V0 от хронографа в м/с.", "Enter one V0 from the chronograph in m/s."))
            return
        }
        val shots = ArrayList<Shot>()
        for (row in rows) {
            val rowV0 = if (shared) sharedV0 else number(row.muzzle.text?.toString())
            val distance = number(row.distance.text?.toString())
            val speed = number(row.speed.text?.toString())
            if (rowV0 == null && distance == null && speed == null) continue
            if (rowV0 == null || distance == null || speed == null ||
                rowV0 < 30.0 || rowV0 > 1500.0 || distance < 1.0 || distance > 500.0 || speed < 1.0
            ) {
                show(
                    if (shared) {
                        t("На всеки ред: дистанция в метри и скорост в м/с.", "On each row: distance in metres and speed in m/s.")
                    } else {
                        t("На всеки ред: V0 от хронографа, дистанция в метри и скорост в м/с.", "On each row: V0 from the chronograph, distance in metres and speed in m/s.")
                    },
                )
                return
            }
            if (speed >= rowV0) {
                show(
                    t(
                        "На ${fmt(distance, 1)} м скоростта е по-висока от V0 на този изстрел.",
                        "At ${fmt(distance, 1)} m the speed is higher than this shot's V0.",
                    ),
                )
                return
            }
            shots.add(Shot(distance, speed, rowV0))
        }
        if (shots.isEmpty()) {
            show(t("Добавете поне един изстрел: V0, дистанция и скорост.", "Add at least one shot: V0, distance and speed."))
            return
        }
        val token = ++generation
        binding.result.text = t("Смятам…", "Calculating…")
        binding.export.isEnabled = false
        val air = Air.of(temp, alt)
        val exportV0 = shots.map { it.v0 }.average()
        Thread {
            val fit = BcSolver.fit(shots, air)
            val load = LoadFile.Load(name, weight, exportV0, fit.bc, temp, alt, shots)
            runOnUiThread {
                if (token != generation) return@runOnUiThread
                lastFit = fit
                lastLoad = load
                binding.result.text = describe(load, fit)
                binding.export.isEnabled = true
            }
        }.start()
    }

    private fun describe(load: LoadFile.Load, fit: Fit): String {
        val loc = if (bulgarian) Locale("bg", "BG") else Locale.US
        val text = StringBuilder()
        text.append("BC ").append(String.format(loc, "%.4f", fit.bc)).append('\n')
        text.append(t("Средно V0 ", "Mean V0 ")).append(String.format(loc, "%.1f", load.v0)).append(t(" м/с", " m/s"))
        if (load.weightG != null) {
            text.append(t("   маса ", "   mass ")).append(String.format(loc, "%.3f", load.weightG)).append(t(" г", " g"))
        }
        text.append(t("\nСредна разлика ", "\nMean difference ")).append(String.format(loc, "%.1f", fit.meanAbsErrorMps)).append(t(" м/с\n", " m/s\n"))
        if (fit.limited) {
            text.append(t("Точките не се събират в един коефициент на този двигател.\n", "The points do not fit one coefficient on this engine.\n"))
        }
        for (point in fit.points) {
            val delta = point.predictedMps - point.shot.speedMps
            text.append(
                String.format(
                    loc,
                    t(
                        "V0 %.1f, %.1f м: мерено %.1f, модел %.1f (%+.1f), свой BC %.4f\n",
                        "V0 %.1f, %.1f m: measured %.1f, model %.1f (%+.1f), own BC %.4f\n",
                    ),
                    point.shot.v0, point.shot.distanceM, point.shot.speedMps, point.predictedMps, delta, point.ownBc,
                ),
            )
        }
        val spread = fit.points.maxOf { it.ownBc } - fit.points.minOf { it.ownBc }
        if (fit.points.size > 1 && spread > 0.01) {
            text.append(t("Отделните дистанции дават различен BC. Един коефициент е компромис.", "Separate distances give a different BC. One coefficient is a compromise."))
        }
        return text.toString().trim()
    }

    private fun export() {
        val load = lastLoad ?: return
        val fit = lastFit ?: return
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val txtName = "bc-$stamp.txt"
        val jsonName = "bc-$stamp.json"
        val wroteTxt = saveDownload(txtName, "text/plain", readable(load, fit) + "\n" + LoadFile.machineBlock(load))
        val wroteJson = saveDownload(jsonName, "application/json", LoadFile.json(load))
        if (!wroteTxt || !wroteJson) {
            Toast.makeText(this, t("Файлът не се записа.", "The file was not saved."), Toast.LENGTH_LONG).show()
            return
        }
        val note = t("Записано в Download/RealBc:\n$txtName\n$jsonName", "Saved in Download/RealBc:\n$txtName\n$jsonName")
        binding.result.append("\n\n$note")
        Toast.makeText(this, t("Записано в Download/RealBc", "Saved in Download/RealBc"), Toast.LENGTH_LONG).show()
    }

    private fun saveDownload(name: String, mime: String, body: String): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RealBc")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            val wrote = contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
                true
            } ?: false
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            return wrote
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RealBc")
        if (!dir.exists() && !dir.mkdirs()) return false
        File(dir, name).writeText(body, Charsets.UTF_8)
        return true
    }

    private fun readable(load: LoadFile.Load, fit: Fit): String {
        val loc = if (bulgarian) Locale("bg", "BG") else Locale.US
        return buildString {
            appendLine(t("Реален балистичен коефициент", "Real ballistic coefficient"))
            appendLine(t("Снаряд: ${load.name}", "Projectile: ${load.name}"))
            appendLine(t("Средно V0: ${String.format(loc, "%.2f", load.v0)} м/с", "Mean V0: ${String.format(loc, "%.2f", load.v0)} m/s"))
            appendLine("BC: ${String.format(loc, "%.4f", fit.bc)}")
            if (load.weightG != null) appendLine(t("Маса: ${String.format(loc, "%.3f", load.weightG)} г", "Mass: ${String.format(loc, "%.3f", load.weightG)} g"))
            appendLine(t("Температура: ${String.format(loc, "%.1f", load.temperatureC)} °C", "Temperature: ${String.format(loc, "%.1f", load.temperatureC)} °C"))
            appendLine(t("Височина: ${String.format(loc, "%.0f", load.altitudeM)} м", "Altitude: ${String.format(loc, "%.0f", load.altitudeM)} m"))
            appendLine(t("Двигател: ${BcSolver.MODEL}", "Engine: ${BcSolver.MODEL}"))
            appendLine()
            appendLine(t("V0 | дистанция м | измерена | по модела | разлика", "V0 | distance m | measured | model | difference"))
            for (point in fit.points) {
                val delta = point.predictedMps - point.shot.speedMps
                appendLine(String.format(loc, "%.2f | %.2f | %.2f | %.2f | %+.2f",
                    point.shot.v0, point.shot.distanceM, point.shot.speedMps, point.predictedMps, delta))
            }
            appendLine()
            appendLine(t("Машинният блок под линията е за импорт.", "The machine block below the line is for import."))
        }
    }

    private fun show(message: String) {
        binding.result.text = message
        binding.export.isEnabled = false
        lastLoad = null
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun number(raw: String?): Double? {
        val text = raw?.trim()?.replace(',', '.') ?: return null
        if (text.isEmpty() || text == "-" || text == ".") return null
        return text.toDoubleOrNull()
    }

    private fun fmt(value: Double, digits: Int): String =
        String.format(if (bulgarian) Locale("bg", "BG") else Locale.US, "%.${digits}f", value)
}
