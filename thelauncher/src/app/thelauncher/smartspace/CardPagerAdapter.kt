package app.thelauncher.smartspace

import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.preferences2.firstCached
import android.content.Context
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import app.thelauncher.smartspace.model.SmartspaceStyle
import app.thelauncher.smartspace.model.SmartspaceTarget
import com.android.launcher3.R
import com.android.launcher3.util.Themes

class CardPagerAdapter(context: Context) : PagerAdapter() {

    private val currentTextColor = Themes.getAttrColor(context, R.attr.workspaceTextColor)
    private val targets = mutableListOf<SmartspaceTarget>()
    private var smartspaceTargets = targets
    private val holders = SparseArray<ViewHolder>()
    private var extras: List<SmartspaceTarget> = emptyList()
    private var styleVersion = 0

    /** Changing the style rebuilds every card on the next update. */
    var style = SmartspaceStyle()
        set(value) {
            if (field != value) {
                field = value
                styleVersion++
            }
        }

    fun setTargets(newTargets: List<SmartspaceTarget>, newExtras: List<SmartspaceTarget> = emptyList()) {
        targets.clear()
        targets.addAll(newTargets)
        extras = newExtras
        notifyDataSetChanged()
    }

    override fun instantiateItem(container: ViewGroup, position: Int): ViewHolder {
        val target = smartspaceTargets[position]
        val card = createBaseCard(container, getFeatureType(target))
        val viewHolder = ViewHolder(position, card, target, styleVersion)
        onBindViewHolder(viewHolder)
        container.addView(card)
        holders.put(position, viewHolder)
        return viewHolder
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        val viewHolder = obj as ViewHolder
        container.removeView(viewHolder.card)
        if (holders[position] == viewHolder) {
            holders.remove(position)
        }
    }

    fun getCardAtPosition(position: Int) = holders[position]?.card

    override fun getItemPosition(obj: Any): Int {
        val viewHolder = obj as ViewHolder
        if (viewHolder.styleVersion != styleVersion) return POSITION_NONE
        val target = getTargetAtPosition(viewHolder.position)
        if (viewHolder.target === target) {
            // The details line can change while the date card stays the same.
            if (style.oneCard) onBindViewHolder(viewHolder)
            return POSITION_UNCHANGED
        }
        if (target == null ||
            getFeatureType(target) !== getFeatureType(viewHolder.target) ||
            target.id != viewHolder.target.id
        ) {
            return POSITION_NONE
        }
        viewHolder.target = target
        onBindViewHolder(viewHolder)
        return POSITION_UNCHANGED
    }

    private fun getTargetAtPosition(position: Int): SmartspaceTarget? {
        if (position !in 0 until smartspaceTargets.size) {
            return null
        }
        return smartspaceTargets[position]
    }

    private fun onBindViewHolder(viewHolder: ViewHolder) {
        val target = smartspaceTargets[viewHolder.position]
        val card = viewHolder.card
        val textColor = style.textColor ?: currentTextColor
        card.setSmartspaceTarget(target, smartspaceTargets.size > 1 && !style.oneCard)
        card.setPrimaryTextColor(textColor)
        card.applyStyle(style)
        if (style.oneCard) card.setDetails(extras, textColor)
    }

    override fun getCount() = smartspaceTargets.size

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view === (obj as ViewHolder).card
    }

    private fun createBaseCard(
        container: ViewGroup,
        featureType: SmartspaceTarget.FeatureType,
    ): BcSmartspaceCard {
        val centered = PreferenceManager2.getInstance(container.context).smartspaceCenteredDate.firstCached()
        val layout = when {
            style.oneCard -> R.layout.smartspace_card_combined
            featureType == SmartspaceTarget.FeatureType.FEATURE_WEATHER ->
                if (centered) R.layout.smartspace_card_date_inline else R.layout.smartspace_card_date
            else -> R.layout.smartspace_card
        }
        return LayoutInflater.from(container.context)
            .inflate(layout, container, false) as BcSmartspaceCard
    }

    private fun getFeatureType(target: SmartspaceTarget) = target.featureType

    class ViewHolder internal constructor(
        val position: Int,
        val card: BcSmartspaceCard,
        var target: SmartspaceTarget,
        val styleVersion: Int,
    )
}
