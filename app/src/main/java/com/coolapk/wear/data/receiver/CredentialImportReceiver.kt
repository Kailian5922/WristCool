package com.coolapk.wear.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.coolapk.wear.data.repository.AuthRepository

class CredentialImportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.coolapk.wear.IMPORT_CREDENTIALS") {
            val payload = intent.getStringExtra("payload") ?: ""
            if (payload.isNotBlank()) {
                val repository = AuthRepository(context.applicationContext)
                val result = repository.importCompanionPayload(payload)
                result.onSuccess {
                    Toast.makeText(context, "酷安 Wear: 登录凭据导入成功", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "酷安 Wear: 导入失败 ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
