package hu.roncsauto.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import hu.roncsauto.app.databinding.ActivityMainBinding
import hu.roncsauto.app.inspection.InspectionExpiry
import hu.roncsauto.app.inspection.JszpVehicle
import hu.roncsauto.app.ocr.PlateOcr
import hu.roncsauto.app.photo.PhotoDecoder
import hu.roncsauto.app.photo.PhotoStore
import hu.roncsauto.app.plate.HungarianPlate
import hu.roncsauto.app.report.HandoffText
import hu.roncsauto.app.report.ReportText
import hu.roncsauto.app.wizard.ReportSession
import hu.roncsauto.app.wizard.WizardStep
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val plateOcr by lazy { PlateOcr(recognizer) }
    private val session: ReportSession by viewModels()

    private var lastWeakCameraPlate: String? = null
    private var photoGeneration = 0

    private val pickVehiclePhoto =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) importVehiclePhoto(uri)
        }

    private val pickInspectionShot =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) readInspectionScreenshot(uri)
        }

    private val requestCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                session.photoMode = false
                hideStillPhoto()
                startCamera()
            } else {
                toast("Kamera nélkül a saját fotó használható.")
            }
        }

    private val requestWrite =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) saveToGallery() else toast("Nincs írási engedély a galériához.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnPickPhoto.setOnClickListener {
            pickVehiclePhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnCamera.setOnClickListener { showCamera() }
        binding.btnManual.setOnClickListener { showManualPlateDialog() }
        binding.tvPlate.setOnClickListener { showManualPlateDialog() }
        binding.btnSaveGallery.setOnClickListener { requestSaveToGallery() }
        binding.btnSaveGalleryStep3.setOnClickListener { requestSaveToGallery() }
        binding.btnNextFromPlate.setOnClickListener {
            if (session.canAdvanceFromPlate()) {
                session.goNext()
                stopCamera()
                render()
            }
        }
        binding.btnJszp.setOnClickListener { openJszp() }
        binding.btnInspection.setOnClickListener { showInspectionDialog() }
        binding.btnBackFromJszp.setOnClickListener {
            session.goBack()
            render()
            if (session.photoMode && sharedPhotoFile().exists()) displaySavedPhoto()
            else if (cameraGranted()) startCamera()
        }
        binding.btnSkipJszp.setOnClickListener {
            session.goNext()
            render()
        }
        binding.btnNextFromJszp.setOnClickListener {
            session.goNext()
            render()
        }
        binding.btnJarokelo.setOnClickListener { openJarokelo() }
        binding.btnShare.setOnClickListener {
            val plate = session.plate ?: return@setOnClickListener
            sharePhoto(ReportText.build(plate, session.expiry, LocalDate.now(), session.vehicle))
        }
        binding.btnBackFromJarokelo.setOnClickListener {
            session.goBack()
            render()
        }
        binding.btnRestart.setOnClickListener { restartFlow() }

        render()
        if (session.photoMode && sharedPhotoFile().exists()) {
            displaySavedPhoto()
        } else if (session.step == WizardStep.PLATE) {
            session.photoMode = false
            if (cameraGranted()) startCamera()
            else if (savedInstanceState == null) requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun restartFlow() {
        stopCamera()
        hideStillPhoto()
        sharedPhotoFile().delete()
        session.restart()
        lastWeakCameraPlate = null
        render()
        if (cameraGranted()) startCamera()
        else requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun render() {
        val step = session.step
        styleStep(binding.step1, step == WizardStep.PLATE)
        styleStep(binding.step2, step == WizardStep.JSZP)
        styleStep(binding.step3, step == WizardStep.JAROKELO)
        binding.panelPlate.visibility = if (step == WizardStep.PLATE) View.VISIBLE else View.GONE
        binding.panelJszp.visibility = if (step == WizardStep.JSZP) View.VISIBLE else View.GONE
        binding.panelJarokelo.visibility = if (step == WizardStep.JAROKELO) View.VISIBLE else View.GONE
        binding.previewContainer.visibility =
            if (step == WizardStep.PLATE) View.VISIBLE else View.GONE

        val plate = session.plate
        binding.tvPlate.text = if (plate == null) "Rendszám: -" else "Rendszám: $plate"
        binding.tvJszpPlate.text = if (plate == null) "Rendszám: -" else "Rendszám: $plate"
        val hasPhoto = sharedPhotoFile().exists()
        binding.tvPhoto.text = when {
            !hasPhoto -> "Fotó: nincs"
            session.photoSaved -> "Fotó: mentve a galériába"
            else -> "Fotó: kiválasztva"
        }
        binding.tvInspection.text = when (val date = session.expiry) {
            null -> "Műszaki: nincs megadva"
            else -> if (InspectionExpiry.isExpired(date, LocalDate.now())) {
                "Műszaki lejárt: ${ReportText.formatHu(date)}"
            } else {
                "Műszaki nem lejárt: ${ReportText.formatHu(date)}"
            }
        }
        binding.btnNextFromPlate.isEnabled = session.canAdvanceFromPlate()
        binding.btnSaveGallery.isEnabled = hasPhoto
        binding.btnSaveGalleryStep3.isEnabled = hasPhoto
        binding.btnShare.isEnabled = hasPhoto
        binding.btnJszp.isEnabled = plate != null
        binding.btnInspection.isEnabled = plate != null
        binding.btnJarokelo.isEnabled = plate != null
        if (plate != null) {
            val report = ReportText.build(plate, session.expiry, LocalDate.now(), session.vehicle)
            val photoLine = when {
                !hasPhoto -> "Fotó: nincs"
                session.photoSaved -> "Fotó: mentve"
                else -> "Fotó: kész, még nincs a galériában"
            }
            binding.tvSummary.text = "$report\n\n$photoLine"
        } else {
            binding.tvSummary.text = "Nincs rendszám."
        }
    }

    private fun styleStep(view: TextView, active: Boolean) {
        view.setTextColor(if (active) 0xFFFFFFFF.toInt() else 0x88FFFFFF.toInt())
        view.setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun showCamera() {
        if (!cameraGranted()) {
            requestCamera.launch(Manifest.permission.CAMERA)
            return
        }
        session.photoMode = false
        hideStillPhoto()
        startCamera()
        render()
    }

    private fun startCamera() {
        if (!cameraGranted() || session.photoMode || session.step != WizardStep.PLATE) return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            if (isDestroyed || session.photoMode || session.step != WizardStep.PLATE) return@addListener
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
                .build()
                .also { it.setAnalyzer(cameraExecutor, PlateAnalyzer()) }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
                toast("A kamera nem indult el (${e.javaClass.simpleName}).")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!isDestroyed) future.get().unbindAll()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun importVehiclePhoto(uri: Uri) {
        val generation = ++photoGeneration
        session.photoMode = true
        session.photoSaved = false
        stopCamera()
        cameraExecutor.execute {
            var failure: Throwable? = null
            val bitmap = try {
                PhotoDecoder.decode(contentResolver, uri, PlateOcr.OCR_EDGE)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Vehicle photo too large: $uri", e)
                failure = e
                null
            } catch (e: Exception) {
                Log.e(TAG, "Vehicle photo decode failed: $uri", e)
                failure = e
                null
            }
            if (bitmap == null) {
                onUi {
                    if (generation == photoGeneration) {
                        session.photoMode = false
                        hideStillPhoto()
                        toast(
                            if (failure is OutOfMemoryError) "A fotó túl nagy."
                            else "A fotó nem nyitható meg (${failure?.javaClass?.simpleName})."
                        )
                        if (cameraGranted()) startCamera()
                        render()
                    }
                }
                return@execute
            }
            try {
                val dest = sharedPhotoFile()
                val temp = File(dest.parentFile, dest.name + ".tmp")
                val stored = PhotoDecoder.scaleCopy(bitmap, MAX_PHOTO_EDGE)
                try {
                    PhotoDecoder.saveJpeg(stored, temp, JPEG_QUALITY)
                } finally {
                    if (stored !== bitmap) stored.recycle()
                }
                if (!temp.renameTo(dest)) {
                    temp.copyTo(dest, overwrite = true)
                    temp.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Saving vehicle photo failed", e)
                bitmap.recycle()
                onUi {
                    if (generation == photoGeneration) {
                        session.photoMode = false
                        toast("A fotó mentése sikertelen.")
                        if (cameraGranted()) startCamera()
                        render()
                    }
                }
                return@execute
            }
            onUi { toast("Rendszám keresése a fotón…") }
            val foundPlate = try {
                plateOcr.find(bitmap) { isDestroyed || generation != photoGeneration }?.canonical
            } catch (e: Exception) {
                Log.e(TAG, "Plate OCR failed", e)
                null
            }
            if (isDestroyed || generation != photoGeneration) {
                if (!bitmap.isRecycled) bitmap.recycle()
                return@execute
            }
            val display = PhotoDecoder.scaleCopy(bitmap, DISPLAY_EDGE)
            if (display != bitmap && !bitmap.isRecycled) bitmap.recycle()
            onUi {
                showBitmap(display)
                session.setPlate(foundPlate, allowClear = true)
                render()
                if (foundPlate == null) {
                    toast("A fotón nem találtam rendszámot. Írd be a „Kézzel” gombbal.")
                } else {
                    toast("Felismert rendszám: $foundPlate. Ha hibás, javítsd a „Kézzel” gombbal.")
                }
            }
        }
    }

    private fun displaySavedPhoto() {
        val generation = photoGeneration
        cameraExecutor.execute {
            val bitmap = PhotoDecoder.decodeFile(sharedPhotoFile(), DISPLAY_EDGE) ?: return@execute
            onUi {
                if (session.photoMode && generation == photoGeneration) showBitmap(bitmap)
                else if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    private fun showBitmap(bitmap: Bitmap) {
        val previous = (binding.selectedPhoto.drawable as? BitmapDrawable)?.bitmap
        binding.selectedPhoto.setImageBitmap(bitmap)
        binding.selectedPhoto.visibility = View.VISIBLE
        if (previous != null && previous != bitmap && !previous.isRecycled) previous.recycle()
    }

    private fun hideStillPhoto() {
        val previous = (binding.selectedPhoto.drawable as? BitmapDrawable)?.bitmap
        binding.selectedPhoto.setImageDrawable(null)
        binding.selectedPhoto.visibility = View.GONE
        if (previous != null && !previous.isRecycled) previous.recycle()
    }

    private fun openJszp() {
        val plate = session.plate ?: return
        val forClipboard = HandoffText.jszpClipboardPlate(plate)
        copyText(forClipboard)
        MaterialAlertDialogBuilder(this)
            .setTitle("JSZP")
            .setMessage(HandoffText.JSZP_PASTE_HINT)
            .setPositiveButton("Megnyitás") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HandoffText.JSZP_URL)))
            }
            .setNegativeButton("Mégse", null)
            .show()
    }

    private fun showManualPlateDialog() {
        val input = EditText(this).apply {
            hint = "pl. IUX-868 vagy AA BC-123"
            setText(session.plate ?: "")
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Rendszám kézzel")
            .setView(container)
            .setPositiveButton("Mentés", null)
            .setNegativeButton("Mégse", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val typed = HungarianPlate.findBest(input.text.toString())
                if (typed == null) {
                    toast("Nem érvényes rendszám. Példa: IUX-868 vagy AA BC-123.")
                } else {
                    session.setPlate(typed.canonical, allowClear = false)
                    render()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun showInspectionDialog() {
        val plate = session.plate ?: return
        val input = EditText(this).apply {
            hint = "2020.03.15. vagy a JSZP szövege"
            minLines = 4
            maxLines = 8
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val delete = if (session.expiry != null) {
            Button(this).apply { text = "Megadott dátum törlése" }
        } else null
        if (delete != null) container.addView(delete)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Műszaki lejárat")
            .setMessage("Nem automatikus JSZP-lekérdezés. A dátum csak megerősítés után, és csak ha lejárt, kerül a bejelentésbe.")
            .setView(container)
            .setPositiveButton("Ellenőrzés", null)
            .setNeutralButton("Képernyőkép", null)
            .setNegativeButton("Mégse", null)
            .create()
        delete?.setOnClickListener {
            session.clearInspection()
            render()
            dialog.dismiss()
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = InspectionExpiry.analyze(input.text.toString(), plate, LocalDate.now())
                dialog.dismiss()
                confirmInspection(parsed, "Beillesztett szöveg, nem a JSZP válasza.\n\n", plate, JszpVehicle.describe(input.text.toString()))
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                pickInspectionShot.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
        dialog.show()
    }

    private fun readInspectionScreenshot(uri: Uri) {
        val plate = session.plate
        if (plate == null) {
            toast("Előbb kell egy rendszám.")
            return
        }
        cameraExecutor.execute {
            val bitmap = try {
                PhotoDecoder.decode(contentResolver, uri, MAX_PHOTO_EDGE)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Inspection screenshot too large: $uri", e)
                onUi { toast("A kép túl nagy.") }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Inspection screenshot decode failed: $uri", e)
                onUi { toast("A képernyőkép nem nyitható meg (${e.javaClass.simpleName}).") }
                null
            }
            if (bitmap == null) return@execute
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { vision ->
                    val text = vision.text
                    onUi {
                        if (session.plate != plate) {
                            toast("A rendszám közben megváltozott, a dátumot nem veszem át.")
                            return@onUi
                        }
                        val parsed = InspectionExpiry.analyze(text, plate, LocalDate.now())
                        confirmInspection(parsed, "Képernyőképről felismert szöveg, nem a JSZP válasza.\n\n", plate, JszpVehicle.describe(text))
                    }
                }
                .addOnCompleteListener {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
        }
    }

    private fun confirmInspection(
        result: InspectionExpiry.Result,
        prefix: String,
        plate: String,
        vehicle: String?
    ) {
        val vehicleLine = if (vehicle != null) "\n\nJármű: $vehicle" else ""
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("Műszaki dátum")
            .setMessage(prefix + result.summary + vehicleLine)
            .setNegativeButton("Mégse", null)
        if (result.canApply && result.date != null) {
            builder.setPositiveButton("Megerősítem") { _, _ ->
                val stillSame = session.plate != null && HungarianPlate.same(session.plate!!, plate)
                if (stillSame) {
                    session.setInspection(result.date, vehicle)
                    render()
                } else {
                    toast("A rendszám közben megváltozott, a dátumot nem veszem át.")
                }
            }
        } else {
            builder.setPositiveButton("Rendben", null)
        }
        builder.show()
    }

    private fun openJarokelo() {
        val plate = session.plate ?: return
        val report = ReportText.build(plate, session.expiry, LocalDate.now(), session.vehicle)
        copyText(report)
        val hasPhoto = sharedPhotoFile().exists()
        MaterialAlertDialogBuilder(this)
            .setTitle("Járókelő")
            .setMessage(HandoffText.jarokeloInstructions(hasPhoto, session.photoSaved))
            .setPositiveButton("Megnyitás") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HandoffText.JAROKELO_URL)))
            }
            .setNegativeButton("Mégse", null)
            .show()
    }

    private fun sharePhoto(report: String) {
        val file = sharedPhotoFile()
        if (!file.exists()) {
            toast("Nincs fotó.")
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, report)
            putExtra(Intent.EXTRA_SUBJECT, ReportText.subject())
            clipData = ClipData.newUri(contentResolver, "roncs-foto", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        packageManager.queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY).forEach { info ->
            grantUriPermission(
                info.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        startActivity(Intent.createChooser(send, "Fotó átadása"))
    }

    private fun requestSaveToGallery() {
        if (!sharedPhotoFile().exists()) {
            toast("Nincs fotó.")
            return
        }
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        saveToGallery()
    }

    private fun saveToGallery() {
        try {
            val uri = PhotoStore.saveJpeg(this, sharedPhotoFile(), session.plate)
            session.photoSaved = true
            render()
            toast("Fotó elmentve: ${uri.lastPathSegment ?: "galéria"}")
        } catch (e: Exception) {
            Log.e(TAG, "Gallery save failed", e)
            toast("A mentés sikertelen (${e.javaClass.simpleName}).")
        }
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("roncsauto", text))
    }

    private fun sharedPhotoFile(): File = File(cacheDir, "shared/roncs-foto.jpg")

    private fun cameraGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun onUi(block: () -> Unit) {
        if (isDestroyed) return
        runOnUiThread {
            if (!isDestroyed) block()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class PlateAnalyzer : ImageAnalysis.Analyzer {
        @androidx.annotation.OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage == null || session.photoMode || session.step != WizardStep.PLATE) {
                imageProxy.close()
                return
            }
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { vision ->
                    val match = HungarianPlate.findBest(vision.text)
                    val plate = when {
                        match == null -> null
                        match.score >= TRUSTED_CAMERA_SCORE -> match.canonical
                        match.compact == lastWeakCameraPlate -> match.canonical
                        else -> null
                    }
                    lastWeakCameraPlate = match?.compact
                    if (!session.photoMode && session.step == WizardStep.PLATE && plate != null) {
                        runOnUiThread {
                            if (!session.photoMode && session.step == WizardStep.PLATE) {
                                session.setPlate(plate, allowClear = false)
                                render()
                            }
                        }
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        recognizer.close()
    }

    companion object {
        private const val TAG = "RoncsAuto"
        private const val TRUSTED_CAMERA_SCORE = 30
        private const val MAX_PHOTO_EDGE = 2048
        private const val DISPLAY_EDGE = 1280
        private const val JPEG_QUALITY = 85
    }
}
