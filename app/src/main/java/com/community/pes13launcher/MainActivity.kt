
package com.community.pes13launcher

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.database.Cursor
import android.provider.OpenableColumns
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var fileNameView: TextView
    private lateinit var fileSizeView: TextView
    private lateinit var fileTypeView: TextView
    private lateinit var statusView: TextView

    private val prefs by lazy {
        getSharedPreferences("pes13_settings", MODE_PRIVATE)
    }

    private val green = Color.rgb(40, 220, 120)
    private val dark = Color.rgb(9, 17, 13)
    private val panel = Color.rgb(20, 34, 26)
    private val muted = Color.rgb(175, 190, 180)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        restoreSelection()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(dark)
        }

        val scroll = ScrollView(this).apply {
            fillViewport = true
            addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val label = TextView(this).apply {
            text = "COMMUNITY EDITION"
            textSize = 12f
            letterSpacing = 0.16f
            setTextColor(green)
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "PES 13"
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        val subtitle = TextView(this).apply {
            text = "MOBILE FILE MANAGER"
            textSize = 14f
            letterSpacing = 0.08f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 26)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(panel)
        }

        val cardTitle = TextView(this).apply {
            text = "SELECTED GAME FILE"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            setPadding(0, 0, 0, 14)
        }

        fileNameView = makeInfoView()
        fileSizeView = makeInfoView()
        fileTypeView = makeInfoView()

        card.addView(cardTitle)
        card.addView(makeFieldLabel("FILE NAME"))
        card.addView(fileNameView)
        card.addView(makeFieldLabel("FILE SIZE"))
        card.addView(fileSizeView)
        card.addView(makeFieldLabel("FILE TYPE"))
        card.addView(fileTypeView)

        statusView = TextView(this).apply {
            text = "Choose a file to get started."
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 8)
        }

        val selectButton = Button(this).apply {
            text = "SELECT / CHANGE FILE"
            setTextColor(Color.BLACK)
            setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(green)
            )
            setOnClickListener { selectFile() }
        }

        val clearButton = Button(this).apply {
            text = "CLEAR SELECTION"
            setOnClickListener { clearSelection() }
        }

        val footer = TextView(this).apply {
            text = "FILE MANAGER PROTOTYPE\nGame runtime not included."
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 0)
        }

        root.addView(label)
        root.addView(title)
        root.addView(subtitle)

        root.addView(
            card,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            selectButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
        )

        root.addView(
            clearButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(statusView)
        root.addView(footer)

        setContentView(scroll)
    }

    private fun makeFieldLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            setPadding(0, 10, 0, 3)
        }
    }

    private fun makeInfoView(): TextView {
        return TextView(this).apply {
            text = "Not selected"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 2)
        }
    }

    private fun selectFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

        startActivityForResult(intent, 100)
    }

    @Deprecated("Uses the compatible activity result callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != 100 || resultCode != RESULT_OK) return

        val uri = data?.data ?: return

        try {
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                 Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            if (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                contentResolver.takePersistableUriPermission(uri, flags)
            }

            val info = getFileInfo(uri)

            prefs.edit()
                .putString("file_uri", uri.toString())
                .putString("file_name", info.name)
                .apply()

            displayFile(info)
            statusView.text = "File selected and saved on this device."
        } catch (e: Exception) {
            statusView.text =
                "Could not open this file. Please select it again."
        }
    }

    private data class FileInfo(
        val name: String,
        val size: Long?,
        val type: String
    )

    private fun getFileInfo(uri: Uri): FileInfo {
        var name = "Unknown file"
        var size: Long? = null

        var cursor: Cursor? = null

        try {
            cursor = contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE
                ),
                null,
                null,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex =
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: name
                }

                val sizeIndex =
                    cursor.getColumnIndex(OpenableColumns.SIZE)

                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex).takeIf { it >= 0 }
                }
            }
        } catch (_: Exception) {
            // Some file providers don't supply file details.
        } finally {
            cursor?.close()
        }

        val extension = name.substringAfterLast('.', "")
            .lowercase(Locale.ROOT)

        val mimeType = try {
            contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }

        val type = when {
            extension.isNotBlank() -> extension.uppercase(Locale.ROOT)
            !mimeType.isNullOrBlank() -> mimeType
            else -> "Unknown"
        }

        return FileInfo(name, size, type)
    }

    private fun formatSize(bytes: Long?): String {
        if (bytes == null) return "Unavailable"
        if (bytes < 1024) return "$bytes bytes"

        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = -1

        do {
            value /= 1024.0
            index++
        } while (value >= 1024 && index < units.lastIndex)

        return String.format(
            Locale.getDefault(),
            "%.2f %s",
            value,
            units[index.coerceAtMost(units.lastIndex)]
        )
    }

    private fun displayFile(info: FileInfo) {
        fileNameView.text = info.name
        fileSizeView.text = formatSize(info.size)
        fileTypeView.text = info.type
    }

    private fun restoreSelection() {
        val savedUri = prefs.getString("file_uri", null)

        if (savedUri == null) {
            showEmptyState()
            return
        }

        val uri = Uri.parse(savedUri)

        val hasPermission = contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }

        if (!hasPermission) {
            showEmptyState()
            statusView.text = "Permission expired. Please select the file again."
            return
        }

        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { }
                ?: throw Exception("File unavailable")

            displayFile(getFileInfo(uri))
            statusView.text = "Saved selection restored."
        } catch (_: Exception) {
            showEmptyState()
            statusView.text = "File unavailable. Please select it again."
        }
    }

    private fun showEmptyState() {
        fileNameView.text = "Not selected"
        fileSizeView.text = "—"
        fileTypeView.text = "—"
    }

    private fun clearSelection() {
        val savedUri = prefs.getString("file_uri", null)

        if (savedUri != null) {
            try {
                contentResolver.releasePersistableUriPermission(
                    Uri.parse(savedUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Permission may already have been released.
            }
        }

        prefs.edit().clear().apply()
        showEmptyState()
        statusView.text = "Selection cleared."
    }
}
