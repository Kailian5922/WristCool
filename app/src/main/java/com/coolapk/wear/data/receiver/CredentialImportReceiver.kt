package com.coolapk.wear.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.coolapk.wear.WristCoolApplication
import com.coolapk.wear.data.api.DeviceProfile
import com.coolapk.wear.data.repository.AuthRepository

class CredentialImportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_IMPORT_CREDENTIALS) {
            val payload = intent.getStringExtra(EXTRA_PAYLOAD).orEmpty()
            if (payload.isNotBlank()) {
                val app = context.applicationContext as? WristCoolApplication
                val repository = app?.container?.authRepository
                    ?: AuthRepository(context.applicationContext, DeviceProfile(context.applicationContext))
                val result = repository.importCompanionPayload(payload)
                result.onSuccess {
                    Toast.makeText(context, "酷安 Wear: 登录凭据导入成功", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    val deviceOnly = repository.importDeviceIdOnly(payload)
                    if (deviceOnly.isSuccess) {
                        Toast.makeText(context, "酷安 Wear: 数盟设备 ID 导入成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "酷安 Wear: 导入失败 ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_IMPORT_CREDENTIALS = "com.coolapk.wear.IMPORT_CREDENTIALS"
        const val EXTRA_PAYLOAD = "payload"
    }
}
