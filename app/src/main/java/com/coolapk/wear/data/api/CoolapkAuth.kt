package com.coolapk.wear.data.api

import android.util.Base64
import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest

/**
 * 酷安 Token V3 离线签名器
 * 核心算法与 coolapk-desktop (auth.rs) 完全一致。
 */
class CoolapkAuth(private var deviceCode: String) {

    companion object {
        private const val APP_ID = "com.coolapk.market"
        private const val APP_CODE = 2604201L
        private const val STD_BASE64_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

        private const val AUTH_BLOB_B64 =
            "VFRCVU9GUXNRMEVsTFVNa1dERWpRU0VzVXlFbUxETkFVeTFEUEZZdUl5MGlNVU01SXpCVUpGY3RNeWtoTVVRMUpTMURPU1V4TXpoUUxWTXdWMDBzSXloU01UUXNXVEZEUkZFd1EwUlZNU1FrVVMwaktTTXRKRGhZTFZRa1VqRXpLU1FzTTBCVkxUUXdVaTAwTlNNc00yQlhMVk1sSkMxRE5GbE5MQ1EwV1RFekxTUXhRMEVoTEVNbElqRXpLRmN3TXowbExpTkVWekVqUFNZdU5Ea2tMRk13Vml4VEtGa3RRemhYTEZNc1dDNGpMU1l0VTJCVFRUQTBMRkV1TXpoVExqTWhJakZETUZNc0kwQlZMQ1FrVUN3ekpTSXNJekJTTUVNOFVDeEVMU0l0STJCUUxVUW9VeTBqTEZNc0l5VWtNRlF3VmswdFJDeFlNVE1sSkM0ME5TRXdVMFJRTEZNaElpMGtOU013VTJCUUxqTmdVeXdqT0ZFc05DeFRMRU13V0N4VUxTRXRJeVJUTEROQkl5MDBKRlpOTVVNc1VEQkVKRmd0TkMwa0xEUWtXQzFETUZNeFJDa2pMVk0wVml4VE1GZ3hJeTBtTFZNeEpTMHpKRlF4TTJCUUxTUTRXVEJETUZNdUkwVWhUU3d6UEZndFJEQlJNRE5FVVMwak9GWXVKRGtsTVNRMFV6QXpOU013UkRra01UTWtWU3hVTUZRdFJDeFlNVE5GSXl4RUxGWXNJelVoTGlRMFYwMHhOQ3hWTENNMFZ5NHpKRlV4TkN4VExGUTRWeTRqWUZZeEpEVWpMVU13VXpGRFBGSXNNMEVsTERNc1ZEQTBPU011TkNrbUxFUWtWaXhFTVNKTkxUTW9VaXd6TEZjdFUwUlFMaVF3VlRGREpTVXdVeWtqTVVNaEl5MHpJU1VzSTBFak1UTXdVUzFVTkZjeFJERWxMVVF0SlM0a05GSXNJemhaVFRCVFlGRXdReVJZTFNNbElTNDBMRmdzVkRSWkxVUW9XVEJEUUZZdFJDeFdMak5FVkRCRE1Ga3RORFVsTEZRNFZTMVVKRmd3TXpSV0xEUTRVVTB3UXkwbUxVUTRXUzFUTVNNc1V5VWlNVU5BVVN4VUxGWXVJMFJVTGlNc1dERkVMRk14STJCUUxWTTBXUzRrT0Zjd00wUlhMVk1vV0MwaktGRXhMQ00wVml4VE5GQXRSRFJXTFVRbEpDMVRKRkF3TkN4Z1lB"

        private val decodedAuthBlob: ByteArray by lazy {
            Base64.decode(AUTH_BLOB_B64.trim(), Base64.DEFAULT)
        }

        private fun md5Hex(data: ByteArray): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(data)
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun md5Hex(text: String): String = md5Hex(text.toByteArray(Charsets.UTF_8))

        private fun shiftLastBase64Char(value: String, shift: Int): String {
            if (value.isEmpty()) return value
            val prefix = value.substring(0, value.length - 1)
            val lastChar = value.last()
            val index = STD_BASE64_ALPHABET.indexOf(lastChar)
            if (index == -1) return value
            val shifted = ((index + shift) % 64 + 64) % 64
            return prefix + STD_BASE64_ALPHABET[shifted]
        }
    }

    fun setDeviceCode(code: String) {
        this.deviceCode = code
    }

    fun getAppToken(): String {
        val nowSec = System.currentTimeMillis() / 1000L
        for (offset in 0..15) {
            try {
                return generateTokenAt(nowSec + offset)
            } catch (_: Exception) {
                // 向前探测有效盐
            }
        }
        return generateTokenAt(nowSec)
    }

    private fun generateTokenAt(timestamp: Long): String {
        val index = (((timestamp + APP_CODE) % 100) * 4 + 0x80).toInt()
        val chunk = decodedAuthBlob.copyOfRange(index, index + 0x80)
        val segment = Base64.decode(chunk, Base64.DEFAULT)

        val deviceMd5 = md5Hex(deviceCode)
        val plain = "$APP_ID&${String(segment, Charsets.UTF_8)}&$deviceMd5&$timestamp&$APP_CODE"

        val password = md5Hex(Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
        val saltSource = Base64.encodeToString(
            "${timestamp.toString(16)}/${md5Hex(plain)}".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        ).trimEnd('=')

        val salt22 = shiftLastBase64Char(saltSource.take(22), -5)
        // jbcrypt 仅接受 $2a$ 前缀；计算完成后将前缀替换为 $2y$，与酷安官方服务端及桌面版 100% 对齐
        val saltForBcrypt = "$2a$04$$salt22"
        val hash2a = BCrypt.hashpw(password, saltForBcrypt)
        val hash2y = "$2y" + hash2a.substring(3)

        val b64Hash = Base64.encodeToString(hash2y.toByteArray(Charsets.UTF_8), Base64.NO_WRAP).trimEnd('=')
        return "v3$b64Hash"
    }
}
