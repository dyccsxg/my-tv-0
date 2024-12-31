package com.lizongying.mytv0.models

import android.util.Log
import com.lizongying.mytv0.MainActivity
import com.lizongying.mytv0.SP
import com.lizongying.mytv0.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Timer
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.timer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class CustomTVList {
    companion object {
        const val TAG = "CustomTVList"
        const val DEFAULT_SERVER_URL = "https://gitee.com/usm/notes/raw/master/tv/default.json"
        const val SXBC_GET_URL = "http://toutiao.cnwest.com/static/v1/group/stream.js"
        const val M1905_GET_URL = "https://profile.m1905.com/mvod/liveinfo.php"
        const val M1905_AK = "AKLTgt7vZATzQL6jB6Lb"
        const val TC_TV1_GET_URL = "https://www.tcrbs.com/tvradio/tczhpd.html"
        const val TC_TV2_GET_URL = "https://www.tcrbs.com/tvradio/tcggpd.html"
        const val TV189_CCTV6_GET_URL = "https://h5.nty.tv189.com/bff/apis/user/authPlayLive?contentId=C8000000000000000001703664302519"
        const val CCTV1_LOGO = "https://gitee.com/usm/notes/raw/master/tv/logo/cctv1.png"
        const val CCTV6_LOGO = "https://gitee.com/usm/notes/raw/master/tv/logo/cctv6.png"
        const val CCTV8_LOGO = "https://gitee.com/usm/notes/raw/master/tv/logo/cctv8.png"
        const val CCTV9_LOGO = "https://gitee.com/usm/notes/raw/master/tv/logo/cctv9.png"
        const val CCTV10_LOGO = "https://gitee.com/usm/notes/raw/master/tv/logo/cctv10.png"
        const val CCTV17_LOGO = "https://gitee.com/usm/notes/raw/master/tv/logo/cctv17.png"
        const val MIGU_PLAY_URL = "https://webapi.miguvideo.com/gateway/playurl/v2/play/playurlh5?clientId=729ecbc3982b4d46b5b888e9b20848de&startPlay=true&devId=aHRqb2lnN3U3NWwxd2JseWeJRBsvtcTie47vXBoML2sn2ig3LwNijUJnx6a5rsEh&xh265=false&channelId=0131_201600010010024&rateType=3&contId="
    }
    private val m1905CCTV6 = mapOf(
        "title" to "1905电影频道",
        "cid" to "999999",
        "streamname" to "LIVEI56PNI726KA7A",
        "uuid" to "6c9e124e-26f3-49d4-af14-50b5f8303402",
        "playerid" to "587422994314341",
        "page" to "https://www.1905.com/cctv6/live/",
        "appid" to "W0hUwz8D"
    )
    private val m1905XL = mapOf(
        "title" to "1905高清影院",
        "cid" to 999994,
        "streamname" to "LIVENCOI8M4RGOOJ9",
        "uuid" to "02d761af-25be-4745-af1d-4e24fcc1b861",
        "playerid" to "969474391143086",
        "page" to "https://www.1905.com/xl/live/",
        "appid" to "W0hUwz8D"
    )
    private val miguVideo = arrayOf(
        arrayOf("CCTV 1", "608807420", "zyyw", CCTV1_LOGO),
        arrayOf("CCTV 6", "624878396", "zyww", CCTV6_LOGO),
        arrayOf("CCTV 8", "624878356", "zyww", CCTV8_LOGO),
        arrayOf("CCTV 9", "673168140", "zybw", CCTV9_LOGO)
    )
    private lateinit var refreshTvUriTimer: Timer

    /**
     * 加载自定义电视频道
     */
    fun loadCustomTvList() {
        CoroutineScope(Dispatchers.IO).launch {
            val customTvList = mutableListOf<TV>()
            loadTv189("CCTV6 电影", TV189_CCTV6_GET_URL, customTvList)
            load1905(m1905CCTV6, customTvList)
            load1905(m1905XL, customTvList)
            withContext(Dispatchers.Main) {
                appendTvList(customTvList)
            }

            val defaultTvList = mutableListOf<TV>()
            loadDefaultChannels(defaultTvList)
            withContext(Dispatchers.Main) {
                updateTvUri(defaultTvList, true)
            }

            customTvList.clear()
            loadMiguVideo("CCTV 1", miguVideo[0][1], miguVideo[0][2], miguVideo[0][3], customTvList)
            loadMiguVideo("CCTV 6", miguVideo[1][1], miguVideo[1][2], miguVideo[1][3], customTvList)
            loadMiguVideo("CCTV 8", miguVideo[2][1], miguVideo[2][2], miguVideo[2][3], customTvList)
            loadMiguVideo("CCTV 9", miguVideo[3][1], miguVideo[3][2], miguVideo[3][3], customTvList)
            withContext(Dispatchers.Main) {
                appendTvList(customTvList)
            }

            customTvList.clear()
            loadTctv("铜川综合", TC_TV1_GET_URL, customTvList)
            loadTctv("铜川公共", TC_TV2_GET_URL, customTvList)
            withContext(Dispatchers.Main) {
                appendTvList(customTvList)
            }

            customTvList.clear()
            loadSxbc(customTvList)
            withContext(Dispatchers.Main) {
                appendTvList(customTvList)
            }
        }
        refreshTvUri()
    }

    private fun refreshTvUri() {
        if (this::refreshTvUriTimer.isInitialized) {
            refreshTvUriTimer.cancel()
        }
        refreshTvUriTimer = timer("refresh-tv-uri", false, 300000, 300000) {
            CoroutineScope(Dispatchers.IO).launch {
                val customTvList = mutableListOf<TV>()
                load1905(m1905CCTV6, customTvList)
                load1905(m1905XL, customTvList)
                withContext(Dispatchers.Main) {
                    updateTvUri(customTvList, false)
                }
            }
        }
    }

    /**
     * 动态刷新直播源--预留接口, 咱不使用
     */
    fun refreshToken(tvModel: TVModel): Pair<String, Map<String, String>> {
        val m3u8Url = ""
        val headers : Map<String, String> = mapOf()
        return Pair(m3u8Url, headers)
    }

    /**
     * 加载 天翼超高清 资源
     */
    private fun loadTv189(title: String, url: String, customTvList: MutableList<TV>) {
        try {
            val client = getHttpClient()
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
                "看电影",
                listOf())
            customTvList.add(tv)
        } catch (e: Exception) {
            Log.e(TAG, "load tv189 channels error $e")
            "天翼超高清 获取失败".showToast()
        }
    }

    /**
     * 加载 1905 电影网
     */
    private fun load1905(tvConfig: Map<String, Any>, customTvList: MutableList<TV>) {
        try {
            val reqParams = mapOf(
                "cid" to tvConfig["cid"]!!,
                "streamname" to tvConfig["streamname"]!!,
                "uuid" to tvConfig["uuid"]!!,
                "playerid" to tvConfig["playerid"]!!,
                "nonce" to System.currentTimeMillis()/1000,
                "expiretime" to System.currentTimeMillis()/1000 + 600,
                "page" to tvConfig["page"]!!,
                "appid" to tvConfig["appid"]!!
            )
            val authorization = calcSignature(reqParams)
            val jsonBody = com.google.gson.Gson().toJson(reqParams)
            val reqBody = RequestBody.create(okhttp3.MediaType.parse("application/json"), jsonBody)

            val client = getHttpClient()
            val headers = okhttp3.Headers.Builder()
                .add("Content-Type", "application/json")
                .add("Origin", "https://www.1905.com")
                .add("Referer", "https://www.1905.com/")
                .add("Authorization", authorization)
                .build()
            val request = okhttp3.Request.Builder().url(M1905_GET_URL).headers(headers).post(reqBody).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return
            }
            val body = response.body()!!.string()
            if (body.isNullOrBlank()) {
                return
            }

            val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            val respMap: Map<String, Any> = com.google.gson.Gson().fromJson(body, mapType)
            val statusCode = respMap["StatusCode"].toString().toDouble().toInt()
            if (statusCode != 200) {
                return
            }

            val host = getJsonProperty(respMap, "data.quality.hd.host")
            val path = getJsonProperty(respMap, "data.path.hd.uri")
            val sign = getJsonProperty(respMap, "data.sign.hd.hashuri")
            if (host.isEmpty() || path.isEmpty() || sign.isEmpty()) {
                return
            }
            val m3u8Url = host + path + sign
            val tv = TV(0, "", tvConfig["title"] as String,
                "",
                "https://gitee.com/usm/notes/raw/master/tv/logo/1950.png",
                "",
                listOf(m3u8Url),
                mapOf("Origin" to "https://www.1905.com", "Referer" to tvConfig["page"] as String),
                "看电影",
                listOf())
            customTvList.add(tv)
        } catch (e: Exception) {
            Log.e(TAG, "load 1950 movie channels error $e")
        }
    }

    private fun loadMiguVideo(title: String, contId: String, encryptKey: String, logo: String, customTvList: MutableList<TV>) {
        try {
            val fetchPlayUrl = MIGU_PLAY_URL + contId
            val headers = okhttp3.Headers.Builder()
                .add("Origin", "https://m.miguvideo.com")
                .add("Referer", "https://m.miguvideo.com/")
                .add("appCode", "miguvideo_default_h5")
                .build()
            val request = okhttp3.Request.Builder().url(fetchPlayUrl).headers(headers).build()

            val client = getHttpClient()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return
            }
            val body = response.body()!!.string()
            if (body.isNullOrBlank()) {
                return
            }

            val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            val respMap: Map<String, Any> = com.google.gson.Gson().fromJson(body, mapType)
            val statusCode = respMap["code"].toString()
            if (statusCode != "200") {
                return
            }

            var fetchM3u8Url = getJsonProperty(respMap, "body.urlInfo.url")
            if (fetchM3u8Url.isBlank()) {
                return
            }
            val puData = getQueryParamValue(fetchM3u8Url, "puData")
            val ddCalcu = calcDdCalcu(puData, encryptKey)
            fetchM3u8Url += "&ddCalcu=$ddCalcu&sv=10000&crossdomain=www&ct=h5"
            val request2 = okhttp3.Request.Builder().url(fetchM3u8Url).build()
            val response2 = client.newCall(request2).execute()
            if (!response2.isSuccessful) {
                return
            }
            val body2 = response2.body()!!.string()
            if (body2.isNullOrBlank()) {
                return
            }

            val m3u8Url = body2.trim()
            val tv = TV(0, "", title, "", logo, "",
                listOf(m3u8Url),
                mapOf("Origin" to "https://m.miguvideo.com", "Referer" to "https://m.miguvideo.com/"),
                "看央视",
                listOf())
            customTvList.add(tv)
        } catch (e: Exception) {
            Log.e(TAG, "load migu video channels error $e")
            "咪咕视频 获取失败".showToast()
        }
    }

    /**
     * 加载 铜川电视台
     */
    private fun loadTctv(title: String, url: String, customTvList: MutableList<TV>) {
        try {
            val client = getHttpClient()
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
     * 加载 陕西网络广播电视台
     */
    private fun loadSxbc(customTvList: MutableList<TV>) {
        try {
            val client = getHttpClient()
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
     * 自动加载默认频道
     */
    private fun loadDefaultChannels(defaultTvList: MutableList<TV>) {
        try {
            val client = getHttpClient()
            val request = okhttp3.Request.Builder().url(DEFAULT_SERVER_URL).build()
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
            resetCCTV1(newTvList)

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

    /**
     * 加载 CCTV1
     */
    private fun resetCCTV1(tvList: MutableList<TV> ) {
        try {
            var cctv1: TV? = null
            for (v in tvList) {
                if (v.title == "CCTV-1") {
                    cctv1 = v
                    break
                }
            }
            if (cctv1 == null) {
                return
            }

            val url = "https://www.xttv.com.cn/index/channel/index/id/155.html"
            val client = getHttpClient()
            val headers = okhttp3.Headers.Builder().add("Referer", "https://www.xttv.com.cn/").build()
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
            cctv1.uris = listOf(m3u8Url)
            cctv1.headers = mapOf("Origin" to "https://www.xttv.com.cn", "Referer" to "https://www.xttv.com.cn/")
        } catch (e: Exception) {
            Log.e(TAG, "load cctv1 channels error $e")
            "CCTV1 获取失败".showToast()
        }
    }

    /**
     * 添加电视频道
     */
    private fun appendTvList(customTvList: MutableList<TV>) {
        if (customTvList.isEmpty()) {
            return
        }

        try {
            val oldSize = TVList.listModel.size
            var position = oldSize
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

            var groupIndex = TVList.groupModel.size()
            for ((k, v) in map) {
                val tvListModel = TVListModel(k, groupIndex)
                for ((listIndex, v1) in v.withIndex()) {
                    v1.groupIndex = groupIndex
                    v1.listIndex = listIndex
                    tvListModel.addTVModel(v1)
                }
                TVList.listModel.addAll(v)
                TVList.groupModel.addTVListModel(tvListModel)
                groupIndex++
            }

            MainActivity.getInstance().watch()
            if (SP.position in (oldSize + 1)..<position) {
                startPlay()
            }
        }  catch (e: Exception) {
            Log.e(TAG, "append custom tv channels error $e")
            "添加自定义频道失败".showToast()
        }
    }

    private fun updateTvUri(newTvList: MutableList<TV>, autoPlay: Boolean) {
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
                m.tv.uris = listOf(newUri)
                m.tv.headers = map[title]?.headers
                if (title == currentTitle) {
                    isUriChanged = true
                }
                Log.i(TAG, "$title refresh uri from $oldUri to $newUri")
            }
            if (autoPlay && (SP.position != position || isUriChanged)) {
                startPlay()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Refresh tv uri failed $e")
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

    private fun calcSignature(bodyMap: Map<String, Any>): String {
        val appIdHash = calcSHA1(M1905_AK, bodyMap["appid"].toString())
        val sortedKey = bodyMap.keys.sorted()
        val textBuff = StringBuffer()
        for (propKey in sortedKey) {
            if (propKey == "appid") {
                continue
            }
            if (textBuff.isNotEmpty()) {
                textBuff.append("&")
            }
            val propVal = URLEncoder.encode(bodyMap[propKey].toString(), "UTF-8")
            textBuff.append(propKey).append("=").append(propVal)
        }
        textBuff.append(".").append(appIdHash)

        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(textBuff.toString().toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun calcSHA1(accessKey: String, appId: String): String {
        var text = ""
        val sortedAccessKey = accessKey.toCharArray().sorted().reversed().joinToString("")
        for ( idx in accessKey.indices) {
            if (text.isNotEmpty()) {
                text += "."
            }
            text += accessKey[idx] + "-" + sortedAccessKey[idx]
        }
        text += "1905$appId"

        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(text.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun calcDdCalcu(puData: String, encryptKey: String): String {
        var left = 0
        var right = puData.length - 1
        var keyIndex = 0
        var ddCalcu = ""

        while (right > left) {
            ddCalcu += puData[right].toString() + puData[left].toString()
            if (left in 1..4) {
                ddCalcu += encryptKey[keyIndex].toString()
                keyIndex++
            }
            right--
            left++
        }
        return ddCalcu
    }

    private fun getQueryParamValue(url: String, paramKey: String): String {
        val startIdx = url.indexOf("$paramKey=")
        if (startIdx < 0) {
            return ""
        }
        var endIdx = url.indexOf("&", startIdx)
        if (endIdx < 0) {
            endIdx = url.length
        }
        return url.substring(startIdx + "$paramKey=".length, endIdx)
    }

    private fun getJsonProperty(jsonMap: Map<String, Any>, keyStr: String): String {
        var retVal = ""
        var jsonObj = jsonMap
        val keyArray = keyStr.split(".")
        for (idx in keyArray.indices) {
            val key = keyArray[idx]
            if (!jsonObj.containsKey(key)) {
                break
            }

            if (idx < keyArray.size - 1) {
                jsonObj = jsonObj[key] as Map<String, Any>
            } else {
                retVal = jsonObj[key] as String
            }
        }
        return retVal
    }

    private fun getHttpClient(): OkHttpClient {
        val trustManager = getTrustManager()
        return OkHttpClient().newBuilder()
            .sslSocketFactory(getSSLSocketFactory(trustManager), trustManager)
            .hostnameVerifier(getHostnameVerifier())
            .build()
    }
    private fun getTrustManager(): X509TrustManager {
        return object: X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }
        }
    }

    private fun getSSLSocketFactory(trustManager: TrustManager): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        return sslContext.socketFactory
    }

    private fun getHostnameVerifier(): HostnameVerifier {
        return HostnameVerifier { hostname, session -> true }
    }

    private fun startPlay() {
        if (!TVList.setPosition(SP.position)) {
            TVList.setPosition(0)
        }
    }
}