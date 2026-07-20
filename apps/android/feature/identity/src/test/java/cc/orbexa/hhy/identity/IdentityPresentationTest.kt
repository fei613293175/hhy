package cc.orbexa.hhy.identity

import cc.orbexa.hhy.network.IdentityCallResult
import cc.orbexa.hhy.network.IdentitySessionResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityPresentationTest {
    @Test
    fun formOnlyAcceptsNonBlankFrozenFields() {
        val empty = validateIdentityForm(" ", "")
        assertFalse(empty.isEmpty)
        assertEquals("请输入真实姓名", empty.realName)
        assertEquals("请输入身份证号", empty.idNumber)
        assertTrue(validateIdentityForm("张三", "110101199001010011").isEmpty)
    }

    @Test
    fun serverStatesMapToBusinessResultsWithoutTechnicalFields() {
        fun session(status: String) = IdentitySessionResource("1", status = status, version = 0)
        assertEquals(IdentityResultKind.READY, session("SESSION_CREATED").resultKind())
        assertEquals(IdentityResultKind.PENDING, session("MANUAL_REVIEW").resultKind())
        assertEquals(IdentityResultKind.SUCCESS, session("VERIFIED").resultKind())
        assertEquals(IdentityResultKind.SUCCESS, session("COMPLETED").resultKind())
        assertEquals("认证未通过", session("REJECTED").businessStatusText())
        assertEquals("正在确认认证结果", session("NEW_SERVER_STATE").businessStatusText())
    }

    @Test
    fun failuresUseActionableBusinessCopy() {
        assertEquals(
            "操作过于频繁，请在30秒后重试",
            IdentityCallResult.Failure(429, retryAfterSeconds = 30).businessMessage("提交失败"),
        )
        assertEquals(
            "身份证号格式不正确",
            IdentityCallResult.Failure(422, fieldErrors = mapOf("idNumber" to "身份证号格式不正确"))
                .businessMessage("提交失败"),
        )
    }
}
