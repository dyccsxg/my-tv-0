package com.lizongying.mytv0.models

import android.util.Log
import com.lizongying.mytv0.MainActivity
import com.lizongying.mytv0.SP
import com.lizongying.mytv0.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import java.util.regex.Pattern
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class CustomTVList {
    companion object {
        const val TAG = "CustomTVList"
        const val SXBC_GET_URL = "http://toutiao.cnwest.com/static/v1/group/stream.js"
        const val TC_TV1_GET_URL = "https://www.tcrbs.com/tvradio/tczhpd.html"
        const val TC_TV2_GET_URL = "https://www.tcrbs.com/tvradio/tcggpd.html"
        const val TV189_CCTV6_GET_URL = "https://h5.nty.tv189.com/bff/apis/user/authPlayLive?contentId=C8000000000000000001703664302519"
        const val CF_BASE_URL = "http://cfss.cc"
        const val CCTV1_LOGO = "https://gitee.com/usm/notes/raw/master/tv/logo/cctv1.png"
        const val CCTV6_LOGO = "https://gitee.com/usm/notes/raw/master/tv/logo/cctv6.png"
        const val CCTV8_LOGO = "https://gitee.com/usm/notes/raw/master/tv/logo/cctv8.png"
        const val CCTV9_LOGO = "https://gitee.com/usm/notes/raw/master/tv/logo/cctv9.png"
        const val CCTV10_LOGO = "https://gitee.com/usm/notes/raw/master/tv/logo/cctv10.png"
        const val CCTV17_LOGO = "https://gitee.com/usm/notes/raw/master/tv/logo/cctv17.png"
    }
    private val regexMap = mapOf(
        "CCTV1" to "[^\"]*/cctv1\\-[0-9]*.m3u8",
        "CCTV8" to "[^\"]*/cctv8\\-[0-9]*.m3u8",
        "CCTV9" to "[^\"]*/mk/cctv9\\-[0-9]*.m3u8")

    /**
     * 加载自定义电视频道
     */
    fun loadCustomTvList() {
        CoroutineScope(Dispatchers.IO).launch {
            val customTvList = mutableListOf<TV>()
            loadTv189("CCTV6 电影", TV189_CCTV6_GET_URL, customTvList)
            loadTctv("铜川综合", TC_TV1_GET_URL, customTvList)
            loadTctv("铜川公共", TC_TV2_GET_URL, customTvList)
            loadSxbc(customTvList)
            withContext(Dispatchers.Main) {
                appendTvList(customTvList)
            }

            val defaultTvList = mutableListOf<TV>()
            loadDefaultChannels(defaultTvList)
            withContext(Dispatchers.Main) {
                updateTvUri(defaultTvList)
            }
        }
    }

    /**
     * 更新频道播放地址
     */
    fun updateTvUri() {
        CoroutineScope(Dispatchers.IO).launch {
            val customTvList = mutableListOf<TV>()
            loadTv189("CCTV6 电影", TV189_CCTV6_GET_URL, customTvList)
            loadTctv("铜川综合", TC_TV1_GET_URL, customTvList)
            loadTctv("铜川公共", TC_TV2_GET_URL, customTvList)
            loadSxbc(customTvList)
            loadDefaultChannels(customTvList)
            withContext(Dispatchers.Main) {
                updateTvUri(customTvList)
            }
        }
    }

    /**
     * 刷新 CF 直播源 token
     */
    fun refreshToken(tvModel: TVModel): String {
        var m3u8Url = ""
        try {
            val title = tvModel.tv.title
            val regex = regexMap[title]
            if (regex.isNullOrBlank()) {
                return m3u8Url
            }

            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url("$CF_BASE_URL/ds/").build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return m3u8Url
            }
            val body = response.body()!!.string()
            val pattern = Pattern.compile("\"($regex)\"")
            val matcher = pattern.matcher(body)
            if (!matcher.find()) {
                return m3u8Url
            }
            var playerUrl = matcher.group(1) ?: ""
            if (playerUrl.isEmpty()) {
                return m3u8Url
            }
            playerUrl = CF_BASE_URL + playerUrl

            val request2 = okhttp3.Request.Builder().url(playerUrl).build()
            val response2 = client.newCall(request2).execute()
            if (!response2.isSuccessful) {
                return m3u8Url
            }
            val body2 = response2.body()!!.string()
            val pattern2 = Pattern.compile("['\"](.*.m3u8.*)['\"]")
            val matcher2 = pattern2.matcher(body2)
            if (!matcher2.find()) {
                return m3u8Url
            }
            m3u8Url = matcher2.group(1) ?: ""
            if (!m3u8Url.matches(Regex("^https?://.*"))) {
                m3u8Url = CF_BASE_URL + m3u8Url
            }
            tvModel.tv.headers = mapOf("Origin" to CF_BASE_URL, "Referer" to playerUrl)
        } catch (e: Exception) {
            Log.e(TAG, "load cf channels error $e")
            "长风直播源 获取失败".showToast()
        }
        return m3u8Url
    }

    /**
     * 添加电视频道
     */
    private fun appendTvList(customTvList: MutableList<TV>) {
        if (customTvList.isEmpty()) {
            return
        }

        try {
            var position = TVList.listModel.size
            for (v in customTvList) {
                v.id = position
                position += 1
            }
            TVList.list.addAll(customTvList)

            val map: MutableMap<String, MutableList<TVModel>> = mutableMapOf()
            for (v in customTvList) {
                if (v.group !in map) {
                    map[v.group] = mutableListOf()
                }
                map[v.group]?.add(TVModel(v))
            }
            for ((k, v) in map) {
                val tvListModel = TVListModel(k)
                for (v1 in v) {
                    tvListModel.addTVModel(v1)
                }
                TVList.listModel.addAll(v)
                TVList.groupModel.addTVListModel(tvListModel)
            }

            MainActivity.getInstance().watch()
        }  catch (e: Exception) {
            Log.e(TAG, "append custom tv channels error $e")
            "添加自定义频道失败".showToast()
        }
    }

    /**
     * 加载 陕西网络广播电视台
     */
    private fun loadSxbc(customTvList: MutableList<TV>) {
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
    private fun loadTctv(title: String, url: String, customTvList: MutableList<TV>) {
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
                "看铜川",
                listOf())
            customTvList.add(tv)
        } catch (e: Exception) {
            Log.e(TAG, "load tctv channels error $e")
            "铜川电视台 获取失败".showToast()
        }
    }

    /**
     * 加载 天翼超高清 资源
     */
    private fun loadTv189(title: String, url: String, customTvList: MutableList<TV>) {
        try {
            val client = okhttp3.OkHttpClient()
            val headers = okhttp3.Headers.Builder().add("Referer", "https://h5.nty.tv189.com/").build()
            val request = okhttp3.Request.Builder().url(url).headers(headers).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return
            }
            val body = response.body()!!.string()

            var m3u8Url = ""
            val pattern = Pattern.compile("(https?://.*m3u8.*)['\"]")
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                m3u8Url = matcher.group(1) ?: ""
            }
            if (m3u8Url.isEmpty()) {
                return
            }
            val tv = TV(0, "", title,
                "",
                CCTV6_LOGO,
                "",
                listOf(m3u8Url),
                mapOf("Origin" to "https://h5.nty.tv189.com", "Referer" to "https://h5.nty.tv189.com/"),
                "看央视",
                listOf())
            customTvList.add(tv)
        } catch (e: Exception) {
            Log.e(TAG, "load tv189 channels error $e")
            "天翼超高清 获取失败".showToast()
        }
    }

    /**
     * 自动加载默认频道
     */
    private fun loadDefaultChannels(defaultTvList: MutableList<TV>) {
        try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(TVList.serverUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return
            }
            val body = response.body()!!.string()
            if (body.isNullOrEmpty()) {
                return
            }

            val type = object : com.google.gson.reflect.TypeToken<List<TV>>() {}.type
            val newTvList: MutableList<TV> = com.google.gson.Gson().fromJson(body, type)
            defaultTvList.addAll(newTvList)

            // 保存频道
            val file = File(TVList.appDirectory, TVList.FILE_NAME)
            if (!file.exists()) {
                file.createNewFile()
            }
            file.writeText(body)
        } catch (e: Exception) {
            Log.e(TAG, "load default channels error $e")
            "预置频道 更新失败".showToast()
        }
    }

    private fun updateTvUri(newTvList: MutableList<TV>) {
        if (newTvList.isEmpty()) {
            return
        }

        try {
            val map: MutableMap<String, TV> = mutableMapOf()
            for (v in newTvList) {
                map[v.title] = v
            }

            val position = TVList.position.value?:0
            val currentTitle = TVList.getTVModel(position).tv.title
            var isUriChanged = false
            for (m in TVList.listModel) {
                val title = m.tv.title
                val oldUri = m.videoUrl.value
                val newUri = map[title]?.uris?.get(0) ?: ""
                if (newUri.isBlank() || newUri == oldUri) {
                    continue
                }

                m.setVideoUrl(newUri)
                m.tv.headers = map[title]?.headers
                if (title == currentTitle) {
                    isUriChanged = true
                }
                Log.i(TAG, "$title refresh uri from $oldUri to $newUri")
            }
            if (SP.position != position || isUriChanged) {
                startPlay()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Refresh tv uri failed $e")
            "頻道地址 更新失败".showToast()
        }
    }

    private fun startPlay() {
        if (!TVList.setPosition(SP.position)) {
            TVList.setPosition(0)
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