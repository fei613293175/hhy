package cc.orbexa.hhy.chat

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R14BlockStateStoreInstrumentedTest {
    @Test
    fun successfulValueSurvivesStoreRecreationAndStaysAccountPeerScoped() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val account = "instrument-account"
        val peer = "instrument-peer"
        val first = SharedPreferencesR14BlockStateStore(context)
        first.setBlockedByMe(account, peer, false)

        first.setBlockedByMe(account, peer, true)
        val recreated = SharedPreferencesR14BlockStateStore(context)
        assertTrue(recreated.isBlockedByMe(account, peer))
        assertFalse(recreated.isBlockedByMe("other-account", peer))
        assertFalse(recreated.isBlockedByMe(account, "other-peer"))

        recreated.setBlockedByMe(account, peer, false)
        assertFalse(SharedPreferencesR14BlockStateStore(context).isBlockedByMe(account, peer))
    }
}
