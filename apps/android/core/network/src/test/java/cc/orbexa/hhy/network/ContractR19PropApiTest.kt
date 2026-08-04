package cc.orbexa.hhy.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContractR19PropApiTest {
    @Test
    fun pageRouteEncodesFiltersWithoutChangingTheirMeaning() {
        assertEquals(
            "/api/v1/me/props?page=2&pageSize=20&status=AVAILABLE&keyword=%E5%A4%B4%E6%9D%A1&sort=expiresAt%3Aasc",
            buildR19PropPageRoute(
                "/api/v1/me/props", 2, 20, "AVAILABLE", "头条", "expiresAt:asc",
            ),
        )
    }

    @Test
    fun pageRouteRejectsUnknownPathsAndInvalidBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            buildR19PropPageRoute("/api/v1/admin/props", 1, 20, null, null, "name:asc")
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildR19PropPageRoute("/api/v1/props/store", 0, 20, null, null, "name:asc")
        }
    }
}
