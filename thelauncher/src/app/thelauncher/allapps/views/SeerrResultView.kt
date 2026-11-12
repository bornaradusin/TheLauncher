package app.thelauncher.allapps.views

import android.content.Context
import android.content.Intent
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import app.thelauncher.font.FontManager
import app.thelauncher.launcher
import app.thelauncher.search.adapter.SearchTargetCompat
import app.thelauncher.seerr.SeerrClient
import app.thelauncher.seerr.SeerrColors
import app.thelauncher.seerr.SeerrItem
import app.thelauncher.seerr.SeerrMode
import app.thelauncher.seerr.SeerrSheets
import app.thelauncher.seerr.SeerrStatus
import app.thelauncher.seerr.SeerrTargets
import app.thelauncher.ui.preferences.PreferenceActivity
import app.thelauncher.ui.preferences.navigation.SeerrSettings
import coil.load
import com.android.launcher3.R
import kotlinx.coroutines.launch

/** A Seerr row in search: a match to add or delete, "More results in Seerr", or a message. */
class SeerrResultView(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs),
    SearchResultView {

    private lateinit var poster: ImageView
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var action: TextView

    private var kind = SeerrTargets.KIND_MESSAGE
    private var item: SeerrItem? = null
    private var mode = SeerrMode.ADD
    private var onlyMatch = false
    private var term = ""
    private var busy = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        poster = findViewById(R.id.seerr_poster)
        title = findViewById(R.id.seerr_title)
        subtitle = findViewById(R.id.seerr_subtitle)
        action = findViewById(R.id.seerr_action)
        val fonts = FontManager.INSTANCE.get(context)
        fonts.setCustomFont(title, R.id.font_body_medium)
        fonts.setCustomFont(subtitle, R.id.font_body)
        fonts.setCustomFont(action, R.id.font_button)
        poster.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 6 * resources.displayMetrics.density)
            }
        }
        poster.clipToOutline = true
    }

    override val isQuickLaunch: Boolean
        get() = kind == SeerrTargets.KIND_ITEM && onlyMatch && mode == SeerrMode.ADD &&
            item?.let { !it.isAvailable && !it.isRequested } == true

    override val titleText: CharSequence? get() = item?.title

    override fun launch(): Boolean {
        if (!isQuickLaunch) return false
        onActionClick()
        return true
    }

    override fun bind(target: SearchTargetCompat, shortcuts: List<SearchTargetCompat>) {
        val extras = target.extras
        kind = extras.getString(SeerrTargets.EXTRA_KIND) ?: SeerrTargets.KIND_MESSAGE
        mode = extras.getString(SeerrTargets.EXTRA_MODE)?.let { runCatching { SeerrMode.valueOf(it) }.getOrNull() } ?: SeerrMode.ADD
        onlyMatch = extras.getBoolean(SeerrTargets.EXTRA_ONLY_MATCH, false)
        term = extras.getString(SeerrTargets.EXTRA_TERM).orEmpty()
        item = extras.getString(SeerrTargets.EXTRA_ITEM)?.let { SeerrItem.fromJson(it) }
        busy = false

        when (kind) {
            SeerrTargets.KIND_ITEM -> bindItem()
            SeerrTargets.KIND_MORE -> {
                poster.isVisible = false
                title.text = context.getString(R.string.seerr_more_results)
                subtitle.isVisible = true
                subtitle.text = context.resources.getQuantityString(
                    R.plurals.seerr_match_count,
                    extras.getInt(SeerrTargets.EXTRA_TOTAL),
                    extras.getInt(SeerrTargets.EXTRA_TOTAL),
                )
                action.isVisible = false
                setOnClickListener { openSeerrSearch() }
            }
            else -> {
                poster.isVisible = false
                title.text = target.searchAction?.title
                subtitle.isVisible = false
                action.isVisible = false
                val setUp = term == "SET_UP" || term == "UNREACHABLE" || term == "NO_LOCAL_NETWORK"
                setOnClickListener(
                    if (setUp) {
                        OnClickListener { context.startActivity(PreferenceActivity.createIntent(context, SeerrSettings)) }
                    } else {
                        null
                    },
                )
            }
        }
    }

    private fun bindItem() {
        val item = item ?: return
        poster.isVisible = true
        poster.background = GradientDrawable().apply { setColor(SeerrColors.placeholder(context)) }
        poster.setImageDrawable(null)
        item.posterUrl?.let { url -> poster.load(url) { crossfade(true) } }
        title.text = item.title
        subtitle.isVisible = true
        subtitle.text = subtitleFor(item)
        renderAction(item)
        setOnClickListener {
            SeerrSheets.showInfo(context.launcher, item, mode) { status -> updateStatus(status) }
        }
    }

    private fun subtitleFor(item: SeerrItem): String = listOfNotNull(
        item.year,
        context.getString(if (item.isSeries) R.string.seerr_series else R.string.seerr_movie),
        if (item.status == SeerrStatus.PARTIALLY_AVAILABLE) context.getString(R.string.seerr_partly_available) else null,
    ).joinToString(" · ")

    private fun renderAction(item: SeerrItem) {
        action.isVisible = true
        action.setOnClickListener(null)
        action.isClickable = false
        val colors = SeerrColors.resolve(context)
        when {
            mode == SeerrMode.DELETE -> {
                action.text = context.getString(R.string.seerr_delete)
                action.setTextColor(colors.dangerText)
                action.background = pill(colors.dangerBg)
                action.setOnClickListener { onActionClick() }
            }
            item.isAvailable -> {
                action.text = context.getString(R.string.seerr_available)
                action.setTextColor(colors.availableText)
                action.background = pill(colors.availableBg)
            }
            item.isRequested -> {
                action.text = context.getString(R.string.seerr_requested)
                action.setTextColor(colors.requestedText)
                action.background = pill(colors.requestedBg)
            }
            else -> {
                action.text = context.getString(if (busy) R.string.seerr_adding else R.string.seerr_add)
                action.setTextColor(colors.buttonText)
                action.background = pill(colors.buttonBg)
                action.setOnClickListener { onActionClick() }
            }
        }
    }

    private fun onActionClick() {
        val item = item ?: return
        val launcher = context.launcher
        when {
            mode == SeerrMode.DELETE -> SeerrSheets.showDelete(launcher, item) { status -> updateStatus(status) }
            item.isSeries -> SeerrSheets.showSeasons(launcher, item) { status -> updateStatus(status) }
            busy -> Unit
            else -> {
                val client = SeerrClient.fromSettings(context) ?: return
                busy = true
                renderAction(item)
                launcher.lifecycleScope.launch {
                    val result = runCatching { client.request(item) }
                    busy = false
                    result.onSuccess {
                        Toast.makeText(context, context.getString(R.string.seerr_requested_toast, item.title), Toast.LENGTH_SHORT).show()
                        updateStatus(SeerrStatus.PENDING)
                    }.onFailure {
                        Toast.makeText(context, context.getString(R.string.seerr_failed, it.message ?: ""), Toast.LENGTH_LONG).show()
                        renderAction(item)
                    }
                }
            }
        }
    }

    /** Shows what changed after adding or deleting, until the next search. */
    private fun updateStatus(status: Int) {
        val current = item ?: return
        val updated = current.copy(status = status, mediaId = if (status == SeerrStatus.UNKNOWN) null else current.mediaId)
        item = updated
        if (mode == SeerrMode.DELETE && status == SeerrStatus.UNKNOWN) {
            action.isVisible = true
            action.text = context.getString(R.string.seerr_deleted)
            action.setTextColor(SeerrColors.resolve(context).requestedText)
            action.background = pill(SeerrColors.resolve(context).requestedBg)
            action.setOnClickListener(null)
            action.isClickable = false
            subtitle.text = subtitleFor(updated)
            return
        }
        subtitle.text = subtitleFor(updated)
        renderAction(updated)
    }

    private fun openSeerrSearch() {
        val intent = SeerrTargets.searchIntent(context, term) ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = 18 * resources.displayMetrics.density
    }
}
