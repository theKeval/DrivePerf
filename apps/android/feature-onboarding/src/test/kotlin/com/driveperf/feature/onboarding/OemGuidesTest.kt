package com.driveperf.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F-ONB-07: Samsung, Xiaomi/Redmi/POCO, OnePlus, Oppo, Vivo, Huawei and Honor get steps; everything else is skipped. */
class OemGuidesTest {
    @Test
    fun `every listed brand maps to its guide, whatever the capitalisation`() {
        assertEquals(OemBrand.Samsung, OemGuides.forDevice("samsung")?.brand)
        assertEquals(OemBrand.Samsung, OemGuides.forDevice("SAMSUNG")?.brand)
        assertEquals(OemBrand.Xiaomi, OemGuides.forDevice("Xiaomi")?.brand)
        assertEquals(OemBrand.OnePlus, OemGuides.forDevice("OnePlus")?.brand)
        assertEquals(OemBrand.Oppo, OemGuides.forDevice("OPPO")?.brand)
        assertEquals(OemBrand.Vivo, OemGuides.forDevice("vivo")?.brand)
        assertEquals(OemBrand.Huawei, OemGuides.forDevice("HUAWEI")?.brand)
        assertEquals(OemBrand.Honor, OemGuides.forDevice("HONOR")?.brand)
    }

    @Test
    fun `Redmi and POCO phones get the Xiaomi guide by manufacturer or brand`() {
        assertEquals(OemBrand.Xiaomi, OemGuides.forDevice("Xiaomi", "Redmi")?.brand)
        assertEquals(OemBrand.Xiaomi, OemGuides.forDevice("Xiaomi", "POCO")?.brand)
        assertEquals(OemBrand.Xiaomi, OemGuides.forDevice("unknown", "Redmi")?.brand)
        assertEquals(OemBrand.Xiaomi, OemGuides.forDevice("", "poco")?.brand)
    }

    @Test
    fun `a Pixel and other brands have no guide`() {
        assertNull(OemGuides.forDevice("Google", "google"))
        assertNull(OemGuides.forDevice("Motorola", "motorola"))
        assertNull(OemGuides.forDevice("Sony", "Sony"))
        assertNull(OemGuides.forDevice("realme", "realme"))
        assertNull(OemGuides.forDevice(null, null))
        assertNull(OemGuides.forDevice("  ", ""))
    }

    @Test
    fun `a listed brand wins over an unlisted brand string`() {
        assertEquals(OemBrand.Samsung, OemGuides.forDevice("samsung", "google")?.brand)
    }

    @Test
    fun `every brand has a title, a lead and at least one step with a path`() {
        OemBrand.entries.forEach { brand ->
            val guide = assertNotNull_(OemGuides.forDevice(brand.label))
            assertTrue(guide.steps.isNotEmpty())
            assertEquals(brand, guide.brand)
        }
    }

    @Test
    fun `Samsung has two steps, Xiaomi three with a last step that has no button, OnePlus two`() {
        assertEquals(2, OemGuides.forDevice("samsung")!!.steps.size)
        val xiaomi = OemGuides.forDevice("xiaomi")!!.steps
        assertEquals(3, xiaomi.size)
        assertNull(xiaomi.last().button)
        assertTrue(xiaomi.last().links.isEmpty())
        assertEquals(2, OemGuides.forDevice("oneplus")!!.steps.size)
    }

    @Test
    fun `the Samsung sleeping apps button goes to the lool package first`() {
        val link = OemGuides.forDevice("samsung")!!.steps.first().links.first()
        assertTrue(link is GuideLink.Component && link.packageName == "com.samsung.android.lool")
    }

    @Test
    fun `no guide asks for the battery exemption itself while OQ-12 is open`() {
        assertFalse(BatteryExemptionPolicy.MAY_REQUEST_EXEMPTION_DIRECTLY)
        // Every link is a brand screen or a system settings page, never a request intent.
        val links = OemBrand.entries.flatMap { OemGuides.forDevice(it.label)!!.steps.flatMap(GuideStep::links) }
        assertTrue(links.all { it is GuideLink.Component || it is GuideLink.BatteryOptimisationSettings || it is GuideLink.AppDetails })
    }

    private fun assertNotNull_(guide: OemGuide?): OemGuide {
        assertNotNull(guide)
        return guide!!
    }
}
