package com.citroncode.screenshotmockupgenerator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.documentfile.provider.DocumentFile
import codes.side.andcolorpicker.converter.toColorInt
import com.citroncode.screenshotmockupgenerator.databinding.ActivityMainBinding
import com.citroncode.screenshotmockupgenerator.databinding.DialogColorPickerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dialogBinding: DialogColorPickerBinding
    var selectScreenshotLauncher: ActivityResultLauncher<Intent>? = null
    private lateinit var alertDialog: AlertDialog
    var REQ_CODE_EXTERNAL_STORAGE_PERMISSION = 23
    var isGradientUsed: Boolean = false
    var startColor: Int = 0
    var endColor: Int = 0
    lateinit var screenshot: Bitmap
    lateinit var background: Bitmap
    lateinit var frame: Bitmap
    lateinit var mergedScreenshot: Bitmap
    var defaultBackgroundColor : Int = Color.parseColor("#128C7F")
    private var selectedDirectoryUri: Uri? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadFrame()

        binding.btnSave.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            openDirectoryLauncher.launch(intent)
        }
        binding.btnChooseColors.setOnClickListener {
            colorPickerDialog()
        }
        binding.ivMockup.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                == PackageManager.PERMISSION_GRANTED
            ) {
                selectScreenshot()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    REQ_CODE_EXTERNAL_STORAGE_PERMISSION
                )
            }
        }

        selectScreenshotLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data!!
                val uri = data.data
                if (uri != null) {
                    val inputStream = contentResolver.openInputStream(uri)
                    screenshot = BitmapFactory.decodeStream(inputStream)
                    binding.ivMockup.setImageBitmap(mergeBitmaps())
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.error_returned_uri_is_null), Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    }
    private val openDirectoryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val treeUri = result.data?.data
                if (treeUri != null) {
                    selectedDirectoryUri = treeUri
                    saveImageToDirectory()
                }
            }
        }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CODE_EXTERNAL_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                selectScreenshot()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.the_permission_is_needed_to_select_a_screenshot),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun selectScreenshot() {
        val intent = Intent()
        intent.setType("image/*")
        intent.setAction(Intent.ACTION_GET_CONTENT)
        selectScreenshotLauncher?.launch(intent)
    }

    fun loadFrame() {
        val drawable: Drawable? = ContextCompat.getDrawable(this, R.drawable.frame_pixel)
        if (drawable != null) {
            frame = drawable.toBitmap()
        }
        background = colorizeBitmap(defaultBackgroundColor)
    }

    fun mergeBitmaps(): Bitmap {
        mergedScreenshot = Bitmap.createBitmap(1400, 3000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mergedScreenshot)


        canvas.drawBitmap(background, 0f, 0f, null)


        val centerXscreenshot = ((mergedScreenshot.width - screenshot.width) / 2f) - 5f
        val centerYscreenshot = ((mergedScreenshot.height - screenshot.height) / 2f) - 12f
        canvas.drawBitmap(screenshot, centerXscreenshot, centerYscreenshot, null)

        val centerXframe = (mergedScreenshot.width - frame.width) / 2f
        val centerYframe = (mergedScreenshot.height - frame.height) / 2f
        canvas.drawBitmap(frame, centerXframe, centerYframe, null)

        return mergedScreenshot
    }

    fun colorPickerDialog() {
        dialogBinding = DialogColorPickerBinding.inflate(layoutInflater)

        val builder = MaterialAlertDialogBuilder(this)
        builder.setView(dialogBinding.root)

        alertDialog = builder.create()
        alertDialog.show()

        dialogBinding.cbUseGradient.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                dialogBinding.llEndColor.visibility = View.VISIBLE
                dialogBinding.tvColor.setText(R.string.start_gradient_color)
                isGradientUsed = true
            } else {
                dialogBinding.llEndColor.visibility = View.GONE
                dialogBinding.tvColor.setText(R.string.background_color)
                isGradientUsed = false
            }
        }

        dialogBinding.btnSaveColors.setOnClickListener {
            startColor = dialogBinding.startColor.pickedColor.toColorInt()
            endColor = dialogBinding.endColor.pickedColor.toColorInt()

            background = if (isGradientUsed) {
                createGradientBitmap(startColor, endColor)
            }else{
                colorizeBitmap(startColor)
            }
            binding.ivMockup.setImageBitmap(mergeBitmaps())
            alertDialog.dismiss()
        }
    }

    fun createGradientBitmap(color1: Int, color2: Int): Bitmap {
        val width = 1400
        val height = 3000

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmap)

        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            color1, color2,
            Shader.TileMode.CLAMP
        )

        val paint = Paint()
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        return bitmap
    }

    fun colorizeBitmap(color: Int): Bitmap {
        val colorizedBitmap = Bitmap.createBitmap(1400, 3000, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(colorizedBitmap)

        val paint = Paint()
        paint.color = color
        paint.isAntiAlias = true

        canvas.drawRect(0f, 0f, 1400.toFloat(), 3000.toFloat(), paint)

        return colorizedBitmap
}

    private fun saveImageToDirectory() {
        if (selectedDirectoryUri != null) {
            val timeStamp =
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "mockup_$timeStamp.png"

            try {
                val selectedDirectoryDocumentFile =
                    DocumentFile.fromTreeUri(this, selectedDirectoryUri!!)

                if (selectedDirectoryDocumentFile != null && selectedDirectoryDocumentFile.exists()) {
                    val imageFile =
                        selectedDirectoryDocumentFile.createFile("image/png", imageFileName)

                    imageFile?.let { outputFile ->
                        contentResolver.openOutputStream(outputFile.uri)?.use { outputStream ->
                            mergedScreenshot.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                            Toast.makeText(
                                this,
                                "Bild erfolgreich gespeichert.",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }
                    } ?: run {
                        Toast.makeText(this, "Fehler beim Erstellen der Datei.", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Fehler beim Speichern des Bildes.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
}