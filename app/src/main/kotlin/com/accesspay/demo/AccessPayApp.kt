package com.accesspay.demo

import android.app.Application
import com.accessai.android.AccessAI
import com.accessai.android.AccessAIConfig
import com.accesspay.demo.ai.AccessAiSessionTracker
import com.accesspay.demo.ai.registerAccessPayActions
import com.accesspay.demo.bank.BankApi
import com.accesspay.demo.bank.BankRepository
import com.accesspay.demo.bank.OnDeviceBank

class AccessPayApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Blank = the simulated bank inside the app, so the APK runs on any phone with only AccessAI online.
        bank = BankRepository(BuildConfig.MOCK_BANK_URL.ifBlank { null }?.let(::BankApi) ?: OnDeviceBank())
        AccessAI.init(
            this,
            // Blank = offline LocalRulesBackend for development; the mock bank must then allow dev tokens.
            AccessAIConfig(appId = "accesspay-demo", serverUrl = BuildConfig.ACCESSAI_URL.ifBlank { null }),
        )
        registerAccessPayActions(bank, navigator, aiTracker)
    }

    companion object {
        lateinit var bank: BankRepository
            private set
        val navigator = Navigator()
        val aiTracker = AccessAiSessionTracker()
    }
}
