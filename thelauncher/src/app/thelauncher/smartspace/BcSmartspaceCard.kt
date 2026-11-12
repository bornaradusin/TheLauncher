package app.thelauncher.smartspace

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.Gravity
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.layoutDirection
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.preferences2.firstCached
import app.thelauncher.smartspace.model.SmartspaceAction
import app.thelauncher.smartspace.model.SmartspaceStyle
import app.thelauncher.smartspace.model.SmartspaceTarget
import app.thelauncher.smartspace.model.hasIntent
import com.android.launcher3.R
import java.util.Locale
import java.util.UUID

class BcSmartspaceCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private var baseActionIconSubtitleView: DoubleShadowTextView? = null
    private var dateView: IcuDateTextView? = null
    private var dndImageView: ImageView? = null
    private var extrasGroup: ViewGroup? = null
    private var iconDrawable: DoubleShadowIconDrawable? = null
    private var iconTintColor = 0
    private var nextAlarmImageView: ImageView? = null
    private var nextAlarmTextView: TextView? = null
    private var subtitleTextView: TextView? = null
    private lateinit var target: SmartspaceTarget
    private var titleTextView: TextView? = null
    private var topPadding = 0
    private var usePageIndicatorUi = false
    private var detailsView: TextView? = null
    private var bubbleView: LinearLayout? = null
    private val baseTextSizes = mutableMapOf<TextView, Float>()

    override fun onFinishInflate() {
        super.onFinishInflate()
        dateView = findViewById(R.id.date)
        titleTextView = findViewById(R.id.title_text)
        subtitleTextView = findViewById(R.id.subtitle_text)
        baseActionIconSubtitleView = findViewById(R.id.base_action_icon_subtitle)
        extrasGroup = findViewById(R.id.smartspace_extras_group)
        topPadding = paddingTop
        detailsView = findViewById(R.id.smartspace_details)
        bubbleView = findViewById(R.id.smartspace_text_block)
        listOfNotNull(dateView, titleTextView, subtitleTextView, baseActionIconSubtitleView, detailsView)
            .forEach { baseTextSizes[it] = it.textSize }
        extrasGroup?.let {
            dndImageView = it.findViewById(R.id.dnd_icon)
            nextAlarmImageView = it.findViewById(R.id.alarm_icon)
            nextAlarmTextView = it.findViewById(R.id.alarm_text)
        }
    }

    fun setSmartspaceTarget(target: SmartspaceTarget, multipleCards: Boolean) {
        this.target = target
        val headerAction = target.headerAction
        val baseAction = target.baseAction
        usePageIndicatorUi = multipleCards

        if (headerAction != null) {
            iconDrawable = BcSmartSpaceUtil.getIconDrawable(headerAction.icon, context)
                ?.let { DoubleShadowIconDrawable(it, context) }

            var title: CharSequence? = headerAction.title
            var subtitle = headerAction.subtitle
            val hasTitle = target.featureType == SmartspaceTarget.FeatureType.FEATURE_WEATHER ||
                !title.isNullOrEmpty()
            val hasSubtitle = !subtitle.isNullOrEmpty()
            if (!hasTitle) {
                title = subtitle
            }
            val contentDescription = headerAction.contentDescription
            setTitle(title, contentDescription, hasTitle != hasSubtitle)
            if (!hasTitle || !hasSubtitle) {
                subtitle = null
            }
            setSubtitle(subtitle, headerAction.contentDescription)
            updateIconTint()
        }

        if (baseAction != null && baseActionIconSubtitleView != null) {
            val icon = BcSmartSpaceUtil.getIconDrawable(baseAction.icon, context)
                ?.let { DoubleShadowIconDrawable(it, context) }
            val iconView = baseActionIconSubtitleView!!
            if (icon != null) {
                icon.setTintList(null)
                iconView.text = baseAction.subtitle
                iconView.setCompoundDrawablesRelative(icon, null, null, null)
                iconView.isVisible = true
                BcSmartSpaceUtil.setOnClickListener(iconView, baseAction, null, "BcSmartspaceCard")
                setFormattedContentDescription(iconView, baseAction.subtitle, baseAction.contentDescription)
            } else {
                iconView.isInvisible = true
                iconView.setOnClickListener(null)
                iconView.contentDescription = null
            }
        }

        dateView?.let {
            // The date opens the app picked in settings, or the default calendar.
            val calendarApp = PreferenceManager2.getInstance(context).smartspaceCalendarApp.firstCached()
            val calendarIntent = calendarApp.takeIf { it.isNotBlank() }
                ?.let { context.packageManager.getLaunchIntentForPackage(it) }
                ?: BcSmartSpaceUtil.getOpenCalendarIntent()
            val calendarAction = SmartspaceAction(
                id = headerAction?.id ?: baseAction?.id ?: UUID.randomUUID().toString(),
                title = "unusedTitle",
                intent = calendarIntent,
            )
            BcSmartSpaceUtil.setOnClickListener(it, calendarAction, null, "BcSmartspaceCard")
        }

        when {
            headerAction.hasIntent -> {
                BcSmartSpaceUtil.setOnClickListener(this, headerAction, null, "BcSmartspaceCard")
            }

            baseAction.hasIntent -> {
                BcSmartSpaceUtil.setOnClickListener(this, baseAction, null, "BcSmartspaceCard")
            }

            else -> {
                BcSmartSpaceUtil.setOnClickListener(this, headerAction, null, "BcSmartspaceCard")
            }
        }
    }

    /** Text size and alignment. */
    fun applyStyle(style: SmartspaceStyle) {
        baseTextSizes.forEach { (view, size) ->
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * style.textScale)
        }
        val bubble = bubbleView ?: return
        val horizontalGravity = if (style.centered) Gravity.CENTER_HORIZONTAL else Gravity.START
        gravity = Gravity.TOP or horizontalGravity
        bubble.gravity = horizontalGravity
        (bubble.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.gravity = horizontalGravity
            bubble.layoutParams = lp
        }
    }

    /** The details line under the date: the most important other items, each with its icon. */
    fun setDetails(extras: List<SmartspaceTarget>, textColor: Int) {
        val view = detailsView ?: return
        val iconSize = view.textSize.toInt()
        val text = SpannableStringBuilder()
        var tapAction: SmartspaceAction? = null
        extras.take(MAX_DETAILS).forEach { target ->
            val action = target.headerAction ?: target.baseAction ?: return@forEach
            val label = listOf(action.title, action.subtitle)
                .filter { !it.isNullOrBlank() }
                .joinToString(" · ")
            if (label.isEmpty()) return@forEach
            if (text.isNotEmpty()) text.append("   ")
            BcSmartSpaceUtil.getIconDrawable(action.icon, context)?.mutate()?.let { icon ->
                icon.setBounds(0, 0, iconSize, iconSize)
                if (target.featureType != SmartspaceTarget.FeatureType.FEATURE_MEDIA) icon.setTint(textColor)
                val start = text.length
                text.append(" ")
                text.setSpan(ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.append(" ")
            }
            text.append(label)
            if (tapAction == null) tapAction = action
        }
        view.text = text
        view.setTextColor(textColor)
        view.isVisible = text.isNotEmpty()
        if (tapAction != null) {
            BcSmartSpaceUtil.setOnClickListener(view, tapAction, null, "BcSmartspaceCard")
        } else {
            view.setOnClickListener(null)
        }
    }

    fun setPrimaryTextColor(textColor: Int) {
        titleTextView?.setTextColor(textColor)
        dateView?.setTextColor(textColor)
        subtitleTextView?.setTextColor(textColor)
        baseActionIconSubtitleView?.setTextColor(textColor)
        detailsView?.setTextColor(textColor)
        iconTintColor = textColor
        updateIconTint()
    }

    fun setTitle(title: CharSequence?, contentDescription: CharSequence?, hasIcon: Boolean) {
        val titleView = titleTextView ?: return
        val isRTL = Locale.getDefault().layoutDirection == View.LAYOUT_DIRECTION_RTL
        titleView.textAlignment = if (isRTL) TEXT_ALIGNMENT_TEXT_END else TEXT_ALIGNMENT_TEXT_START
        titleView.text = title
        titleView.setCompoundDrawablesRelative(
            if (hasIcon) iconDrawable else null,
            null,
            null,
            null,
        )
        titleView.ellipsize = if (target.featureType == SmartspaceTarget.FeatureType.FEATURE_CALENDAR &&
            Locale.ENGLISH.language == context.resources.configuration.locale.language
        ) {
            TextUtils.TruncateAt.MIDDLE
        } else {
            TextUtils.TruncateAt.END
        }
        if (hasIcon) {
            setFormattedContentDescription(titleView, title, contentDescription)
        }
    }

    private fun setSubtitle(subtitle: CharSequence?, charSequence2: CharSequence?) {
        val subtitleView = subtitleTextView ?: return
        subtitleView.text = subtitle
        subtitleTextView!!.setCompoundDrawablesRelative(
            if (subtitle.isNullOrEmpty()) null else iconDrawable,
            null,
            null,
            null,
        )
        subtitleTextView!!.maxLines = if (target.featureType == SmartspaceTarget.FeatureType.FEATURE_TIPS && !usePageIndicatorUi) 2 else 1
        setFormattedContentDescription(subtitleTextView!!, subtitle, charSequence2)
    }

    private fun setFormattedContentDescription(
        textView: TextView,
        title: CharSequence?,
        contentDescription: CharSequence?,
    ) {
        textView.contentDescription = when {
            title.isNullOrEmpty() -> contentDescription

            !contentDescription.isNullOrEmpty() -> context.getString(
                R.string.generic_smartspace_concatenated_desc,
                contentDescription,
                title,
            )

            else -> title
        }
    }

    private fun updateIconTint() {
        val icon = iconDrawable ?: return
        when (target.featureType) {
            SmartspaceTarget.FeatureType.FEATURE_WEATHER -> icon.setTintList(null)
            else -> icon.setTint(iconTintColor)
        }
    }

    companion object {
        private const val MAX_DETAILS = 2
    }
}
