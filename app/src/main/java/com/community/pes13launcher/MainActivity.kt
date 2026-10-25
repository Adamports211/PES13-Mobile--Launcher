
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
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private val prefs by lazy {
        getSharedPreferences("pes13_settings", MODE_PRIVATE)
    }

    private val green = Color.rgb(40, 220, 120)
    private val dark = Color.rgb(9, 17, 13)
    private val panel = Color.rgb(20, 34, 26)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        restoreSelection()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 32, 24, 32)
            setBackgroundColor(dark)
        }

        val label = TextView(this).apply {
            text = "COMMUNITY EDITION"
            textSize = 12f
            letterSpacing = 0.18f
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
            text = "MOBILE LAUNCHER"
            textSize = 15f
            letterSpacing = 0.12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(panel)
        }

        val fileLabel = TextView(this).apply {
            text = "SELECTED GAME FILE"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
        }

        status = TextView(this).apply {
            text = "No file selected"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 4)
        }

        val hint = TextView(this).apply {
            text = "Your selection is saved on this device."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, 6, 0, 0)
        }

        card.addView(fileLabel)
        card.addView(status)
        card.addView(hint)

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
            text = "FILE SELECTOR PROTOTYPE\nGame runtime not included."
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
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
            ).apply { topMargin = 22 }
        )

        root.addView(
            clearButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(footer)
        setContentView(root)
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

            contentResolver.takePersistableUriPermission(uri, flags)

            val name = getDisplayName(uri)
            prefs.edit()
                .putString("file_uri", uri.toString())
                .putString("file_name", name)
                .apply()

            showSelection(name)
        } catch (e: SecurityException) {
            status.text = "Permission unavailable. Please select again."
        } catch (e: Exception) {
            status.text = "Could not save this file. Try another file."
        }
    }

    private fun restoreSelection() {
        val savedUri = prefs.getString("file_uri", null) ?: run {
            status.text = "No file selected"
            return
        }

        val uri = Uri.parse(savedUri)
        val hasPermission = contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }

        if (!hasPermission) {
            status.text = "Permission expired. Please select the file again."
            return
        }

        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { }
            showSelection(
                prefs.getString("file_name", "Saved file") ?: "Saved file"
            )
        } catch (e: Exception) {
            status.text = "File unavailable. Please select it again."
        }
    }

    private fun getDisplayName(uri: Uri): String {
        var name = "Selected file"
        var cursor: Cursor? = null

        try {
            cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = cursor.getString(index) ?: name
                }
            }
        } catch (_: Exception) {
            // Keep the fallback name.
        } finally {
            cursor?.close()
        }

        return name
    }

    private fun showSelection(name: String) {
        status.text = name
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
                // The permission may already have expired.
            }
        }

        prefs.edit().clear().apply()
        status.text = "No file selected"
    }
}
