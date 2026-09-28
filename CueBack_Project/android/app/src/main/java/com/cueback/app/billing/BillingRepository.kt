package com.cueback.app.billing

import android.app.Activity
import android.content.Context
import com.cueback.app.BuildConfig
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlanOption(
    val id: String,
    val type: PackageType,
    val title: String,
    val price: String,
    val perMonth: String?,
    val trial: String?,
    val rcPackage: Package,
)

sealed interface PurchaseOutcome {
    data object Success : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data object Pending : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}

class BillingRepository(private val context: Context) {
    private val apiKey = BuildConfig.REVENUECAT_API_KEY
    val configured: Boolean get() = apiKey.isNotBlank()

    private val _state = MutableStateFlow<EntitlementState>(if (configured) EntitlementState.Loading else EntitlementState.NotConfigured)
    val state: StateFlow<EntitlementState> = _state.asStateFlow()

    fun configure(appUserId: String) {
        if (!configured || Purchases.isConfigured) return
        if (BuildConfig.DEBUG) Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).appUserID(appUserId).build())
        Purchases.sharedInstance.updatedCustomerInfoListener = com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener { publish(it) }
    }

    suspend fun refresh() {
        if (!configured || !Purchases.isConfigured) return
        try {
            publish(Purchases.sharedInstance.awaitCustomerInfo())
        } catch (e: PurchasesException) {
            if (_state.value !is EntitlementState.Pro && _state.value !is EntitlementState.Free) {
                _state.value = EntitlementState.Error(e.error.message)
            }
        }
    }

    suspend fun plans(): Result<List<PlanOption>> {
        if (!configured || !Purchases.isConfigured) return Result.failure(IllegalStateException("Billing is not configured in this build."))
        return try {
            val offering = Purchases.sharedInstance.awaitOfferings().current
                ?: return Result.failure(IllegalStateException("No current offering is set up in RevenueCat."))
            Result.success(offering.availablePackages.map(::toPlan).sortedBy { if (it.type == PackageType.ANNUAL) 0 else 1 })
        } catch (e: PurchasesException) {
            Result.failure(IllegalStateException(e.error.message))
        }
    }

    suspend fun purchase(activity: Activity, plan: PlanOption): PurchaseOutcome = try {
        val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, plan.rcPackage).build())
        publish(result.customerInfo)
        if (result.customerInfo.entitlements[ENTITLEMENT_PRO]?.isActive == true) PurchaseOutcome.Success
        else PurchaseOutcome.Failed("Purchase completed but Pro is not active yet. Try Restore.")
    } catch (e: PurchasesTransactionException) {
        when {
            e.userCancelled -> PurchaseOutcome.Cancelled
            e.code == PurchasesErrorCode.PaymentPendingError -> PurchaseOutcome.Pending
            else -> PurchaseOutcome.Failed(e.message ?: "Purchase failed")
        }
    }

    suspend fun restore(): PurchaseOutcome = try {
        val info = Purchases.sharedInstance.awaitRestore()
        publish(info)
        if (info.entitlements[ENTITLEMENT_PRO]?.isActive == true) PurchaseOutcome.Success
        else PurchaseOutcome.Failed("No active CueBack Pro purchase found for this Google account.")
    } catch (e: PurchasesException) {
        PurchaseOutcome.Failed(e.error.message)
    }

    private fun publish(info: CustomerInfo) {
        _state.value = entitlementFrom(info)
    }

    private fun toPlan(p: Package): PlanOption {
        val product = p.product
        val trial = product.defaultOption?.freePhase?.billingPeriod?.let { "${it.value}-${it.unit.name.lowercase()} free trial" }
        val title = when (p.packageType) {
            PackageType.ANNUAL -> "Annual"
            PackageType.MONTHLY -> "Monthly"
            else -> product.title
        }
        val perMonth = if (p.packageType == PackageType.ANNUAL) {
            product.pricePerMonth()?.formatted?.let { "$it / month" }
        } else null
        return PlanOption(p.identifier, p.packageType, title, product.price.formatted, perMonth, trial, p)
    }

    companion object {
        fun entitlementFrom(info: CustomerInfo): EntitlementState {
            val ent = info.entitlements[ENTITLEMENT_PRO]
            return if (ent?.isActive == true) {
                EntitlementState.Pro(ent.periodType == PeriodType.TRIAL, ent.expirationDate?.time, ent.willRenew)
            } else EntitlementState.Free
        }
    }
}
