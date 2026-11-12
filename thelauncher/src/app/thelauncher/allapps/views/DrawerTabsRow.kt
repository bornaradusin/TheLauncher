package app.thelauncher.allapps.views

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import app.thelauncher.allapps.TheLauncherAlphabeticalAppsList
import app.thelauncher.extras.DrawerTab
import app.thelauncher.launcher
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.preferences2.firstCached
import com.android.launcher3.R
import com.android.launcher3.allapps.FloatingHeaderRow
import com.android.launcher3.allapps.FloatingHeaderView

class DrawerTabsRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs), FloatingHeaderRow {

    private val prefs2 = PreferenceManager2.getInstance(context)
    private val tabs: List<DrawerTab> = prefs2.drawerTabs.firstCached()
    private val showAll = prefs2.drawerTabsShowAll.firstCached()
    private val chipIndices = mutableListOf<Int>()
    private val chipContainer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val chips = mutableListOf<TextView>()
    private val chipHeight = dp(36)
    private val verticalPadding = dp(6)

    init {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        setPadding(dp(16), verticalPadding, dp(16), verticalPadding)
        addView(chipContainer, LayoutParams(LayoutParams.WRAP_CONTENT, chipHeight))
        visibility = if (tabs.isEmpty()) GONE else VISIBLE
        if (tabs.isNotEmpty()) {
            if (showAll) addChip(context.getString(R.string.drawer_tab_all), -1)
            tabs.forEachIndexed { index, tab -> addChip(tab.name, index) }
            updateSelection()
        }
    }

    private fun addChip(label: String, index: Int) {
        val chip = TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(context.getColorStateList(R.color.all_apps_tab_text))
            setBackgroundResource(R.drawable.all_apps_tabs_background)
            setPadding(dp(18), 0, dp(18), 0)
            isSingleLine = true
            setOnClickListener { selectTab(index) }
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, chipHeight)
        lp.marginEnd = dp(8)
        chipContainer.addView(chip, lp)
        chips.add(chip)
        chipIndices.add(index)
    }

    private fun appsList(): TheLauncherAlphabeticalAppsList<*>? = runCatching {
        context.launcher.appsView.personalAppList as? TheLauncherAlphabeticalAppsList<*>
    }.getOrNull()

    private fun selectTab(index: Int) {
        appsList()?.selectTab(index)
        runCatching { context.launcher.appsView.activeRecyclerView?.scrollToTop() }
        updateSelection()
    }

    private fun updateSelection() {
        val list = appsList()
        if (!showAll && tabs.isNotEmpty() && list != null && list.selectedTabIndex < 0) {
            list.selectTab(0)
        }
        val selected = list?.selectedTabIndex ?: -1
        chips.forEachIndexed { i, chip -> chip.isSelected = chipIndices[i] == selected }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateSelection()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(getExpectedHeight(), MeasureSpec.EXACTLY),
        )
    }

    override fun setup(parent: FloatingHeaderView, allRows: Array<FloatingHeaderRow>, tabsHidden: Boolean) {}

    override fun getExpectedHeight(): Int = if (visibility == GONE) 0 else chipHeight + verticalPadding * 2

    override fun shouldDraw(): Boolean = visibility != GONE

    override fun hasVisibleContent(): Boolean = tabs.isNotEmpty()

    override fun setVerticalScroll(scroll: Int, isScrolledOut: Boolean) {
        if (!isScrolledOut) translationY = scroll.toFloat()
        alpha = if (isScrolledOut) 0f else 1f
        if (visibility != GONE) visibility = if (isScrolledOut) INVISIBLE else VISIBLE
    }

    override fun getTypeClass(): Class<out FloatingHeaderRow> = DrawerTabsRow::class.java

    override fun getFocusedChild(): View? = chips.firstOrNull { it.isSelected }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
