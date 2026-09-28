package ai.byak.app.billing

import org.junit.Assert.assertEquals
import org.junit.Test

class BillingCatalogTest {
    @Test
    fun playCatalogMatchesPublishedSubscription() {
        assertEquals("byak_pro", BillingCatalog.PRODUCT_ID)
        assertEquals(listOf("monthly", "yearly"), BillingCatalog.basePlanOrder)
    }

    @Test
    fun basePlansHaveCustomerFacingLabels() {
        assertEquals("BYAK Pro Monthly", BillingCatalog.title("monthly"))
        assertEquals("per month", BillingCatalog.period("monthly"))
        assertEquals("BYAK Pro Yearly", BillingCatalog.title("yearly"))
        assertEquals("per year", BillingCatalog.period("yearly"))
    }
}
