
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

    private lateinit var fileName: TextView
    private lateinit var fileSize: TextView
    private lateinit var fileType: TextView
    private lateinit var message: TextView

    private val prefs by lazy {
        getSharedPreferences("pes13_settings", MODE_PRIVATE)
    }

    private val green = Color.rgb(40, 220, 120)
    private val dark = Color.rgb(9, 17, 13)
    private val panel = Color.rgb(20, 34, 26)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createScreen()
        restoreFile()
    }

    private fun createScreen() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(24, 28, 24, 24)
        root.setBackgroundColor(dark)

        val scroll = ScrollView(this)
        scroll.addView(root)

        val heading = TextView(this)
        heading.text = "PES 13"
        heading.textSize = 40f
        heading.gravity = Gravity.CENTER
        heading.typeface = Typeface.DEFAULT_BOLD
        heading.setTextColor(Color.WHITE)

        val subtitle = TextView(this)
        subtitle.text = "MOBILE FILE MANAGER"
        subtitle.textSize = 14f
        subtitle.gravity = Gravity.CENTER
        subtitle.setTextColor(green)
        subtitle.setPadding(0, 0, 0, 24)

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(18, 18, 18, 18)
        card.setBackgroundColor(panel)

        fileName = newValue()
        fileSize = newValue()
        fileType = newValue()

        addField(card, "FILE NAME", fileName)
        addField(card, "FILE SIZE", fileSize)
        addField(card, "FILE TYPE", fileType)

        val select = Button(this)
        select.text = "SELECT / CHANGE FILE"
        select.setTextColor(Color.BLACK)
        select.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(green)
        )
        select.setOnClickListener { openPicker() }

        val clear = Button(this)
        clear.text = "CLEAR SELECTION"
        clear.setOnClickListener { clearFile() }

        message = TextView(this)
        message.textSize = 12f
        message.gravity = Gravity.CENTER
        message.setTextColor(Color.LTGRAY)
        message.setPadding(0, 14, 0, 14)

        val footer = TextView(this)
        footer.text = "COMMUNITY EDITION\nGame runtime not included."
        footer.textSize = 12f
        footer.gravity = Gravity.CENTER
        footer.setTextColor(Color.GRAY)
        footer.setPadding(0, 12, 0, 0)

        root.addView(heading)
        root.addView(subtitle)
        root.addView(card, fullWidth())
        root.addView(select, fullWidth())
        root.addView(clear, fullWidth())
        root.addView(message, fullWidth())
        root.addView(footer, fullWidth())

        setContentView(scroll)
    }

    private fun newValue(): TextView {
        val view = TextView(this)
        view.text = "Not selected"
        view.textSize = 15f
        view.setTextColor(Color.WHITE)
        view.setPadding(0, 4, 0, 12)
        return view
    }

    private fun addField(
        card: LinearLayout,
        label: String,
        value: TextView
    ) {
        val title = TextView(this)
        title.text = label
        title.textSize = 11f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(green)
        card.addView(title)
        card.addView(value)
    }

    private fun fullWidth(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun openPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(intent, 100)
    }

    @Deprecated("Compatible with this simple launcher")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != 100 || resultCode != RESULT_OK) return

        val uri = data?.data ?: return

        try {
            val readFlag = Intent.FLAG_GRANT_READ_URI_PERMISSION
            val persistFlag = Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            val granted = data.flags

            if ((granted and readFlag) != 0 &&
                (granted and persistFlag) != 0
            ) {
                contentResolver.takePersistableUriPermission(uri, readFlag)
            }

            val name = getName(uri)
            prefs.edit()
                .putString("file_uri", uri.toString())
                .putString("file_name", name)
                .apply()

            showFile(uri)
            message.text = "Selection saved."
        } catch (_: Exception) {
            message.text = "Could not read this file. Try another."
        }
    }

    private fun getName(uri: Uri): String {
        var name = "Unknown file"
        var cursor: Cursor? = null

        try {
            cursor = contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )
                if (index >= 0) {
                    name = cursor.getString(index) ?: name
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }

        return name
    }

    private fun showFile(uri: Uri) {
        fileName.text = getName(uri)

        var size: Long? = null
        var cursor: Cursor? = null

        try {
            cursor = contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) {
                    size = cursor.getLong(index)
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }

        fileSize.text = formatSize(size)

        val extension = getName(uri).substringAfterLast('.', "")
        fileType.text = if (extension.isNotBlank()) {
            extension.uppercase(Locale.ROOT)
        } else {
            contentResolver.getType(uri) ?: "Unknown"
        }
    }

    private fun formatSize(bytes: Long?): String {
        if (bytes == null || bytes < 0) return "Unavailable"
        if (bytes < 1024) return "$bytes bytes"

        val units = arrayOf("KB", "MB", "GB", "TB")
        var amount = bytes.toDouble() / 1024.0
        var index = 0

        while (amount >= 1024.0 && index < units.lastIndex) {
            amount /= 1024.0
            index++
        }

        return String.format(
            Locale.getDefault(),
            "%.2f %s",
            amount,
            units[index]
        )
    }

    private fun restoreFile() {
        val saved = prefs.getString("file_uri", null)

        if (saved == null) {
            emptyFile()
            message.text = "Choose a file to get started."
            return
        }

        val uri = Uri.parse(saved)
        val allowed = contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }

        if (!allowed) {
            emptyFile()
            message.text = "Select your file again to restore access."
            return
        }

        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { }
                ?: throw Exception("File unavailable")

            showFile(uri)
            message.text = "Saved file restored."
        } catch (_: Exception) {
            emptyFile()
            message.text = "File unavailable. Please select it again."
        }
    }

    private fun emptyFile() {
        fileName.text = "Not selected"
        fileSize.text = "—"
        fileType.text = "—"
    }

    private fun clearFile() {
        val saved = prefs.getString("file_uri", null)

        if (saved != null) {
            try {
                contentResolver.releasePersistableUriPermission(
                    Uri.parse(saved),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
        }

        prefs.edit().clear().apply()
        emptyFile()
        message.text = "Selection cleared."
    }
}
