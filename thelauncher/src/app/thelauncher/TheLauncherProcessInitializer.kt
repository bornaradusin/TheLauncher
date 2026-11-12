package app.thelauncher

import android.content.Context
import androidx.annotation.Keep
import app.thelauncher.bugreport.TheLauncherBugReporter
import app.thelauncher.theme.color.tokens.ColorTokens
import com.android.launcher3.Utilities
import com.android.launcher3.icons.mono.ThemedIconDrawable
import com.android.quickstep.QuickstepProcessInitializer

@Keep
class TheLauncherProcessInitializer(context: Context) : QuickstepProcessInitializer(context) {

    override fun init(context: Context) {
        TheLauncherBugReporter.INSTANCE.get(context)
        ThemedIconDrawable.COLORS_LOADER = {
            if (Utilities.isDarkTheme(it)) {
                intArrayOf(
                    ColorTokens.Accent2_800.resolveColor(it),
                    ColorTokens.Accent1_200.resolveColor(it),
                )
            } else {
                intArrayOf(
                    ColorTokens.Accent1_100.resolveColor(it),
                    ColorTokens.Accent1_700.resolveColor(it),
                )
            }
        }
        super.init(context)
    }
}
