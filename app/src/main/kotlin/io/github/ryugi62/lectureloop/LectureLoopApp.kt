package io.github.ryugi62.lectureloop

import android.app.Application
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import io.github.ryugi62.lectureloop.infrastructure.AppContainer

class LectureLoopApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val key = BuildConfig.REVENUECAT_API_KEY
        val configured = key.isNotBlank()
        if (configured) {
            if (BuildConfig.DEBUG) Purchases.logLevel = LogLevel.DEBUG
            Purchases.configure(PurchasesConfiguration.Builder(this, key).build())
        }
        container = AppContainer(this, configured)
        container.revenueCat?.let { rc ->
            Purchases.sharedInstance.updatedCustomerInfoListener = com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener(rc::onCustomerInfo)
        }
    }
}
