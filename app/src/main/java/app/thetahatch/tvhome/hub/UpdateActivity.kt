package app.thetahatch.tvhome.hub

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.thetahatch.tvhome.MainActivity

/** ほかのアプリの「更新する」：Intent("app.thetahatch.action.UPDATE")（extra packageName）→ 更新の画面を開く。 */
class UpdateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SCREEN, "updates")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}
