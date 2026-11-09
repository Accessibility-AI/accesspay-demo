package com.accesspay.demo

import android.app.Application
import com.accessai.android.AccessAI
import com.accessai.android.AccessAIConfig
import com.accesspay.demo.ai.registerAccessPayActions
import com.accesspay.demo.bank.BankApi
import com.accesspay.demo.bank.BankRepository

class AccessPayApp : Application() {

    override fun onCreate() {
        super.onCreate()
        bank = BankRepository(BankApi(BuildConfig.MOCK_BANK_URL))
        AccessAI.init(
            this,
            // Blank = offline LocalRulesBackend for development; the mock bank must then allow dev tokens.
            AccessAIConfig(appId = "accesspay-demo", serverUrl = BuildConfig.ACCESSAI_URL.ifBlank { null }),
        )
        registerAccessPayActions(bank, navigator)
    }

    companion object {
        lateinit var bank: BankRepository
            private set
        val navigator = Navigator()
    }
}
