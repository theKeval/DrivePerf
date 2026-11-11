package com.driveperf.feature.onboarding

import androidx.annotation.StringRes

/** The brands S-ONB-12 and the reliability guide cover (F-ONB-07.1). */
enum class OemBrand(val label: String) {
    Samsung("Samsung"),
    Xiaomi("Xiaomi"),
    OnePlus("OnePlus"),
    Oppo("Oppo"),
    Vivo("Vivo"),
    Huawei("Huawei"),
    Honor("Honor"),
}

/** Where a step's button goes. Each OEM screen is tried in order; the last resort is always a system page. */
sealed interface GuideLink {
    /** An activity of another app (the brand's own settings app). Not part of Android's API: it may not exist. */
    data class Component(val packageName: String, val className: String) : GuideLink

    /** Android's "Battery optimisation" list (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`). */
    data object BatteryOptimisationSettings : GuideLink

    /** DrivePerf's own App info page (`ACTION_APPLICATION_DETAILS_SETTINGS`). */
    data object AppDetails : GuideLink
}

/**
 * One numbered step: what to do, where in Settings, and optionally a button. [links] are tried in order, then
 * [GuideLink.AppDetails]; an empty list means the step has no button (for example Xiaomi's "lock in Recents").
 */
data class GuideStep(
    @StringRes val title: Int,
    @StringRes val path: Int,
    @StringRes val button: Int? = null,
    val links: List<GuideLink> = emptyList(),
)

/** The brand-specific page of S-ONB-12 and S-SET-06. */
data class OemGuide(
    val brand: OemBrand,
    @StringRes val title: Int,
    @StringRes val lead: Int,
    val steps: List<GuideStep>,
)

/**
 * Whether the app may ask for `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` itself. Play only allows it for a narrow list of
 * use cases, and whether trip recording qualifies is **open question OQ-12** (PRD section 9, owner A16, Phase 5). Until it
 * is answered the battery step only links to the system settings page (F-ONB-07.3). Flip this, and add the request
 * intent, only after OQ-12 is closed in `docs/product/decisions.md`.
 */
object BatteryExemptionPolicy {
    const val MAY_REQUEST_EXEMPTION_DIRECTLY: Boolean = false
}

/** Detects the phone's brand from `Build` strings (F-ONB-07.1). Reads nothing else: manufacturer and brand only, locally. */
object OemGuides {
    /**
     * The guide for this phone, or null for a Pixel or any brand not listed (the step is skipped, F-ONB-07.2).
     * Xiaomi's Redmi and POCO phones report "Xiaomi" as manufacturer and "Redmi" or "POCO" as brand; both are matched.
     */
    fun forDevice(manufacturer: String?, brand: String? = null): OemGuide? {
        val brandOf = brandOf(manufacturer) ?: brandOf(brand) ?: return null
        return guides.getValue(brandOf)
    }

    fun brandOf(name: String?): OemBrand? {
        val key = name?.trim()?.lowercase() ?: return null
        return when {
            key.isEmpty() -> null
            key == "samsung" -> OemBrand.Samsung
            key in XIAOMI_NAMES -> OemBrand.Xiaomi
            key == "oneplus" -> OemBrand.OnePlus
            key == "oppo" -> OemBrand.Oppo
            key == "vivo" -> OemBrand.Vivo
            key == "huawei" -> OemBrand.Huawei
            key == "honor" -> OemBrand.Honor
            else -> null
        }
    }

    private val XIAOMI_NAMES = setOf("xiaomi", "redmi", "poco")

    private val guides: Map<OemBrand, OemGuide> = mapOf(
        OemBrand.Samsung to OemGuide(
            OemBrand.Samsung, R.string.onb_oem_samsung_title, R.string.onb_oem_samsung_lead,
            listOf(
                GuideStep(
                    R.string.onb_oem_samsung_step1, R.string.onb_oem_samsung_step1_path, R.string.onb_oem_open_sleeping_apps,
                    listOf(GuideLink.Component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
                ),
                GuideStep(
                    R.string.onb_oem_samsung_step2, R.string.onb_oem_samsung_step2_path, R.string.onb_oem_open_app_battery,
                    listOf(GuideLink.AppDetails),
                ),
            ),
        ),
        OemBrand.Xiaomi to OemGuide(
            OemBrand.Xiaomi, R.string.onb_oem_xiaomi_title, R.string.onb_oem_xiaomi_lead,
            listOf(
                GuideStep(
                    R.string.onb_oem_xiaomi_step1, R.string.onb_oem_xiaomi_step1_path, R.string.onb_oem_open_autostart,
                    listOf(GuideLink.Component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                ),
                GuideStep(
                    R.string.onb_oem_xiaomi_step2, R.string.onb_oem_xiaomi_step2_path, R.string.onb_oem_open_battery_saver,
                    listOf(GuideLink.Component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")),
                ),
                GuideStep(R.string.onb_oem_xiaomi_step3, R.string.onb_oem_xiaomi_step3_path),
            ),
        ),
        OemBrand.OnePlus to OemGuide(
            OemBrand.OnePlus, R.string.onb_oem_oneplus_title, R.string.onb_oem_oneplus_lead,
            listOf(
                GuideStep(
                    R.string.onb_oem_oneplus_step1, R.string.onb_oem_oneplus_step1_path, R.string.onb_oem_open_app_battery,
                    listOf(GuideLink.AppDetails),
                ),
                GuideStep(
                    R.string.onb_oem_oneplus_step2, R.string.onb_oem_oneplus_step2_path, R.string.onb_oem_open_battery_optimisation,
                    listOf(GuideLink.BatteryOptimisationSettings),
                ),
            ),
        ),
        OemBrand.Oppo to template(
            OemBrand.Oppo, R.string.onb_oem_oppo_title, R.string.onb_oem_generic_lead,
            GuideLink.Component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ),
        OemBrand.Vivo to template(
            OemBrand.Vivo, R.string.onb_oem_vivo_title, R.string.onb_oem_generic_lead,
            GuideLink.Component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ),
        OemBrand.Huawei to template(
            OemBrand.Huawei, R.string.onb_oem_huawei_title, R.string.onb_oem_generic_lead,
            GuideLink.Component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ),
        OemBrand.Honor to template(
            OemBrand.Honor, R.string.onb_oem_honor_title, R.string.onb_oem_generic_lead,
            GuideLink.Component("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ),
    )

    /** Oppo, Vivo, Huawei and Honor share one template (design spec S-ONB-12): the brand's startup list, then battery. */
    private fun template(brand: OemBrand, @StringRes title: Int, @StringRes lead: Int, startup: GuideLink) = OemGuide(
        brand, title, lead,
        listOf(
            GuideStep(
                R.string.onb_oem_generic_step1, R.string.onb_oem_generic_step1_path, R.string.onb_oem_open_autostart,
                listOf(startup),
            ),
            GuideStep(
                R.string.onb_oem_generic_step2, R.string.onb_oem_generic_step2_path, R.string.onb_oem_open_battery_optimisation,
                listOf(GuideLink.BatteryOptimisationSettings),
            ),
        ),
    )
}
