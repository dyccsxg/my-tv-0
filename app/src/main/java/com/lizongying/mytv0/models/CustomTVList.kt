package com.lizongying.mytv0.models

import android.util.Log
import com.lizongying.mytv0.MainActivity
import com.lizongying.mytv0.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import java.util.regex.Pattern
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class CustomTVList {
    companion object {
        const val TAG = "ShaanxiTV"
        const val SXBC_GET_URL = "http://toutiao.cnwest.com/static/v1/group/stream.js"
        const val TC_TV1_GET_URL = "https://www.tcrbs.com/tvradio/tczhpd.html"
        const val TC_TV2_GET_URL = "https://www.tcrbs.com/tvradio/tcggpd.html"
    }
    private var customTvList = mutableListOf<TV>()
    private var customTvModelList : List<TVModel> = listOf()

    /**
     * 加载自定义电视频道
     */
    fun loadCustomTvList(tvList: TVList) {
        CoroutineScope(Dispatchers.IO).launch {
            loadSxbc()
            loadTctv("铜川综合", TC_TV1_GET_URL)
            loadTctv("铜川公共", TC_TV2_GET_URL)
            withContext(Dispatchers.Main) {
                appendTvList(tvList)
            }
        }
    }

    /**
     * 添加电视频道
     */
    private fun appendTvList(tvList: TVList) {
        try {
            var position = tvList.listModel.size
            for (v in customTvList) {
                v.id = position
                position += 1
            }
            customTvModelList = customTvList.map { tv -> TVModel(tv) }

            val customTvListModel = TVListModel("看陕西")
            for (tvModel in customTvModelList) {
                customTvListModel.addTVModel(tvModel)
            }
            tvList.groupModel.addTVListModel(customTvListModel)
            tvList.list.addAll(customTvList)
            tvList.listModel.addAll(customTvModelList)
            MainActivity.getInstance().watch()
        }  catch (e: Exception) {
            Log.e(TAG, "append custom tv channels error $e")
            "添加自定义频道失败".showToast()
        }
    }

    /**
     * 加载 陕西网络广播电视台
     */
    private fun loadSxbc() {
        try {
            val client = okhttp3.OkHttpClient()
            val headers = okhttp3.Headers.Builder().add("Referer", "http://live.snrtv.com/").build()
            val request = okhttp3.Request.Builder().url(SXBC_GET_URL).headers(headers).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return
            }
            val body = response.body()!!.string()
            val jsonStr = decryptText(body)
            if (jsonStr.isEmpty()) {
                return
            }

            val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            val tvMap: Map<String, Any> = com.google.gson.Gson().fromJson(jsonStr, mapType)
            val sxbcMap = tvMap["sxbc"] as Map<String, Any>
            for ((_, value) in sxbcMap) {
                val tvObj = value as Map<String, String>
                val title = tvObj["name"]
                val m3u8 = tvObj["m3u8"]
                if (m3u8.isNullOrEmpty() || title.isNullOrEmpty()
                    || title.contains("备用") || title.contains("购物")
                    || title.contains("移动") || title.contains("体育")) {
                    continue
                }

                val tv = TV(0, "", title,
                    "",
                    "https://gitee.com/usm/notes/raw/master/tv/logo/shanxi.png",
                    "",
                    listOf(m3u8),
                    mapOf("Origin" to "http://live.snrtv.com", "Referer" to "http://live.snrtv.com/"),
                    "看陝西",
                    listOf())
                customTvList.add(tv)
            }
        } catch (e: Exception) {
            Log.e(TAG, "load sxbc channels error $e")
            "陕西网络广播电视台 获取失败".showToast()
        }
    }

    /**
     * 加载 铜川电视台
     */
    private fun loadTctv(title: String, url: String) {
        try {
            val client = okhttp3.OkHttpClient()
            val headers = okhttp3.Headers.Builder().add("Referer", "https://www.tcrbs.com/").build()
            val request = okhttp3.Request.Builder().url(url).headers(headers).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return
            }
            val body = response.body()!!.string()

            var m3u8Url = ""
            val pattern = Pattern.compile("(https?://.*auth_key=.*)['\"]")
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                m3u8Url = matcher.group(1) ?: ""
            }
            if (m3u8Url.isEmpty()) {
                return
            }
            val tv = TV(0, "", title,
                "",
                "https://gitee.com/usm/notes/raw/master/tv/logo/tctv.png",
                "",
                listOf(m3u8Url),
                mapOf("Origin" to "https://www.tcrbs.com", "Referer" to "https://www.tcrbs.com/"),
                "看陝西",
                listOf())
            customTvList.add(tv)
        } catch (e: Exception) {
            Log.e(TAG, "load tctv channels error $e")
            "铜川电视台 获取失败".showToast()
        }
    }

    private fun decryptText(text: String): String {
        var strTv = ""
        var strRadio = ""
        val pattern = Pattern.compile("\"([^\"]+)\"")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            strTv = matcher.group(1) ?: ""
        }
        if (matcher.find()) {
            strRadio = matcher.group(1) ?: ""
        }
        if (strTv.isEmpty() || strRadio.isEmpty()) {
            return ""
        }

        val ciphertext = strTv.substring(16)
        val key = strTv.substring(0, 16)
        val iv = strRadio.substring(0, 16)
        return decryptAES(ciphertext, key, iv)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decryptAES(ciphertext: String, key: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(iv.toByteArray(StandardCharsets.UTF_8))
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decryptedBytes = cipher.doFinal(Base64.Default.decode(ciphertext))
        val jsonStr = String(decryptedBytes, StandardCharsets.UTF_8).trim()
        val lastIdx = jsonStr.lastIndexOf('}')
        return jsonStr.substring(0, lastIdx + 1)
    }
}