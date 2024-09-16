package com.adstudio.youtubesummarizer

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.internal.wait

class MainActivity: AppCompatActivity() {

    private val webLink: String = "https://www.youtube.com/watch?v="
    private val shortenedLink: String = "https://youtu.be/"
    private var captionsError = false
    private var summaryError = false

    private lateinit var localAppData: LocalAppData

    private lateinit var summaryType: RadioGroup
    private lateinit var quotaLimitReached: TextView
    private lateinit var urlLink: EditText

    private val apiKey: String = BuildConfig.apiKey
    //Replace the key with your own api key if you wish to build the app yourself ->
    //apiKey: String = fnuqofoi1jej0u32u4824iy509tibguq81ujrf3j

    private var SHORTSUMMARY = "Write a quick summary based on this text: "
    private var LONGSUMMARY = "Write a detailed summary based on this text: "

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        localAppData = LocalAppData()
        localAppData.loadData(this)
        startUpAlert()

        summaryType = findViewById(R.id.summaryType)
        quotaLimitReached = findViewById(R.id.quotaLimitReached)
        urlLink = findViewById(R.id.url)

        quotaLimitReached.text = ""

        val summarize: Button = findViewById(R.id.summarize)
        summarize.setOnClickListener {
            val url: String = urlLink.text.toString()
            if (isValidLink(url)) {
                if (isInternetAvailable()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        processVideo(url)
                    }
                }
                else {
                    internetErrorDialog()
                }
            }
        }
    }

    fun loadingDialog(context: Context): AlertDialog {
        val loading = AlertDialog.Builder(context)
        val loadingView: View = layoutInflater.inflate(R.layout.progress_start_layout, null)
        loading.setView(loadingView)
        loading.setCancelable(false)
        return loading.create()
    }

    fun tokenErrorDialog() {
        val tokenError = AlertDialog.Builder(this)
        tokenError.setTitle("You Have Reached The Quota Limit ")
        tokenError.setMessage("It seems that you have reached the quota limit. Please wait " +
                "at least 1 day for the quota limit to reset")
        tokenError.setPositiveButton("Okay") {dialog, which ->
            dialog.dismiss()
            quotaLimitReached.text = "You have reached the quota limit for today! Try again tomorrow!"
        }
        tokenError.show()
    }

    fun internetErrorDialog() {
        val noInternet = AlertDialog.Builder(this)
        noInternet.setTitle("No Internet Connection Was Found")
        noInternet.setMessage("It seems that there is no internet connection currently. " +
                "To get youtube captions, this app requires an internet connection to work " +
                "properly. Please connect to the internet and try again")
        noInternet.setPositiveButton("Open Settings") {dialog, which ->
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            startActivity(intent)
            dialog.dismiss()
        }
        noInternet.show()
    }

    fun captionErrorDialog() {
        val captionError = AlertDialog.Builder(this)
        captionError.setTitle("There Was An Error Obtaining Captions")
        captionError.setMessage("It seems that there was an error while trying to " +
                "extract the caption from the video. Please ensure the video has " +
                "support for captions and that there is a proper internet connection")
        captionError.setPositiveButton("Okay") {dialog, which ->
            dialog.dismiss()
        }
        captionError.show()
    }

    fun startUpAlert() {
        if (!localAppData.accepted) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Welcome To Youtube Summarizer!")
            builder.setMessage("This app will summarize youtube videos with the help of Google's " +
                    "Gemini AI to give you everything important you need to know. NOTE: This " +
                    "app only works with actual videos. Shorts and other youtube content will not work " +
                    "with this app!!")
            builder.setPositiveButton("Okay") { dialog, which ->
                dialog.dismiss()
                quotaAlert()
                localAppData.accepted = true
                localAppData.saveData(this)
            }
            builder.setCancelable(false)
            builder.show()
        }
    }

    fun quotaAlert() {
        val quotaError = AlertDialog.Builder(this)
        quotaError.setTitle("This App Has A Quota!!!")
        quotaError.setMessage("This app uses a free version of Google's Gemini AI and as such, " +
                "does not provide an unlimited amount of uses. After a certain number of uses, " +
                "you will receive an error saying you have run out of tokens!!!")
        quotaError.setPositiveButton("Okay") {dialog, which ->
            dialog.dismiss()
        }
        quotaError.setCancelable(false)
        quotaError.show()
    }

    private suspend fun processVideo(url: String) {
        val loadingDialog = loadingDialog(this@MainActivity)
        loadingDialog.show()

        val videoId: String = trimUrlLink(url)

        val choice = summaryType.checkedRadioButtonId
        val choiceButton = findViewById<RadioButton>(choice)
        val summaryChoice = choiceButton.text.toString()

        lateinit var generatedSummary: String
        lateinit var captions: String
        val job = CoroutineScope(Dispatchers.Main).launch {
            try {
                captions = runExtractor(videoId)
                if (!captions.equals("Error")) {
                    try {
                        generatedSummary = generateSummary(captions, summaryChoice)
                    }
                    catch(e: Exception) {
                        summaryError = true
                        cancel()
                    }
                }
                else {
                    captionsError = true
                    cancel()
                }
            }
            finally {
                loadingDialog.dismiss()
            }
        }
        job.join()
        if (job.isCompleted && !captionsError && !summaryError) {
            val intent = Intent(applicationContext, SummaryActivity::class.java)
            intent.putExtra("VideoURL", url)
            intent.putExtra("GeneratedSummary", generatedSummary)
            startActivity(intent)
        }
        else if (captionsError) {
            captionErrorDialog()
        }
        else if (summaryError) {
            tokenErrorDialog()
        }
    }

    private fun isValidLink(url: String): Boolean {
        if (url.contains(webLink) || url.contains(shortenedLink)) {
            return true
        }

        if (url.isBlank()) {
            Toast.makeText(this, "You did not enter a youtube link", Toast.LENGTH_LONG).show()
            return false
        }
        else Toast.makeText(this, "This is not a valid youtube link", Toast.LENGTH_LONG).show()
        return false
    }

    private fun trimUrlLink(url: String): String {
        val videoId: String
        if (url.contains(shortenedLink)) {
            val tempUrl: String = url.split("?si=")[0]
            videoId = tempUrl.split(shortenedLink)[1]
        }
        else videoId = url.split(webLink)[1]
        return videoId.trim()
    }

    private fun isInternetAvailable(): Boolean {
        var result = false
        val connectivityManager =
            this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connectivityManager.activeNetwork ?: return false
            val actNw =
                connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
            result = when {
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            connectivityManager.run {
                connectivityManager.activeNetworkInfo?.run {
                    result = when (type) {
                        ConnectivityManager.TYPE_WIFI -> true
                        ConnectivityManager.TYPE_MOBILE -> true
                        ConnectivityManager.TYPE_ETHERNET -> true
                        else -> false
                    }

                }
            }
        }
        return result
    }

    private fun runExtractor(videoId: String): String {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        val py: Python = Python.getInstance()
        val pyObj: PyObject = py.getModule("YoutubeExtractor")
        val pyMethod: PyObject = pyObj.get("caption_extractor")!!

        val captions: String = pyMethod.call(videoId).toString()
        return captions
    }

    private suspend fun generateSummary(captions: String, summaryChoice: String): String {
        lateinit var summaryPrompt: String
        if (summaryChoice.equals("Long")) { summaryPrompt = LONGSUMMARY + captions }
        else { summaryPrompt = SHORTSUMMARY + captions }

        val generativeModel =
            GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey)
        val response = generativeModel.generateContent(summaryPrompt)
        return response.text.toString()
    }
}
