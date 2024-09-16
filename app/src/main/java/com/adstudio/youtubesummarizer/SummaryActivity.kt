package com.adstudio.youtubesummarizer

import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

import com.google.android.material.appbar.MaterialToolbar

import com.squareup.picasso.Picasso

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class SummaryActivity: AppCompatActivity() {

    lateinit var summary: TextView
    lateinit var videoTitle: TextView
    lateinit var videoAuthor: TextView
    lateinit var videoLength: TextView
    lateinit var videoImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val videoURL = intent.getStringExtra("VideoURL").toString()
        val generatedSummary = intent.getStringExtra("GeneratedSummary").toString()

        setContentView(R.layout.activity_summary)
        summary = findViewById(R.id.summary)
        videoImage = findViewById(R.id.videoImage)
        videoTitle = findViewById(R.id.videoTitle)
        videoAuthor = findViewById(R.id.videoAuthor)
        videoLength = findViewById(R.id.videoLength)

        summary.text = generatedSummary
        val videoMetaData = getMetaData(videoURL)
        if (videoMetaData != null) {
            Picasso.get().load(videoMetaData[0]).into(videoImage)
            videoTitle.text = videoMetaData[1]
            videoAuthor.text = "Author: ${videoMetaData[2]}"
            videoLength.text = "Length: ~ ${convertStringMinutes(videoMetaData[3])} minutes"
        }
        else {
            Toast.makeText(this, "There was an error getting some video data", Toast.LENGTH_LONG).show()
        }

        val materialToolbar: MaterialToolbar = findViewById(R.id.toolbar)
        materialToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.download -> {
                    val nameEntryView: View = layoutInflater.inflate(R.layout.file_name_layout, null)
                    val editText: EditText = nameEntryView.findViewById(R.id.fileName)

                    val builder = AlertDialog.Builder(this)
                    builder.setView(nameEntryView)
                    builder.setPositiveButton("Save Summary") { dialog, which ->
                        val fileName: String = editText.text.toString() + ".txt"
                        dialog.dismiss()
                        downloadSummary(fileName, summary.text.toString())
                    }
                    builder.show()
                    true
                }
                else -> false
            }
        }
    }

    private fun convertStringMinutes(timeInMinutes: String): String {
        val longFromString = timeInMinutes.toLong()
        val time = TimeUnit.SECONDS.toMinutes(longFromString)
        return time.toString()
    }

    private fun downloadSummary(fileName: String, summary: String) {
        try {
            val path: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val writer = FileOutputStream(File(path, fileName))
            writer.write(summary.toByteArray())
            writer.close()
        }
        catch (e: Exception) {
            Toast.makeText(this, "There was an error saving the summary", Toast.LENGTH_LONG).show()
        }
        finally {
            Toast.makeText(this, "Summary saved to Downloads", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMetaData(videoURL: String): Array<String> {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        val py: Python = Python.getInstance()
        val pyObj: PyObject = py.getModule("YoutubeMetaData")
        val pyMethod: PyObject = pyObj.get("get_metadata")!!

        val list: Array<String> = pyMethod.call(videoURL).toJava(Array<String>::class.java)

        return list
    }

    override fun onBackPressed() {
        val exitBuilder = AlertDialog.Builder(this)
        exitBuilder.setTitle("Are you sure you want to leave?")
        exitBuilder.setMessage("Leaving now will delete the generated summary and any video data retrieved. " +
            "If you want to save your summary, press the download icon in the top right corner. ")
        exitBuilder.setPositiveButton("Stay") { dialog, which ->
            dialog.dismiss()
        }
        exitBuilder.setNegativeButton("Exit") { dialog, which ->
            super.onBackPressed()
        }
        exitBuilder.show()
    }
}