package app.thelauncher.smartspace

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec.EXACTLY
import android.view.View.MeasureSpec.makeMeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.viewpager.widget.ViewPager
import app.thelauncher.preferences2.PreferenceManager2
import app.thelauncher.smartspace.model.GlanceRanking
import app.thelauncher.smartspace.model.SmartspaceStyle
import app.thelauncher.smartspace.model.SmartspaceTarget
import app.thelauncher.smartspace.provider.SmartspaceProvider
import app.thelauncher.util.repeatOnAttached
import com.android.launcher3.R
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class BcSmartspaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    var previewMode: Boolean = false,
) : FrameLayout(context, attrs) {

    private val provider = SmartspaceProvider.INSTANCE.get(context)

    private lateinit var viewPager: ViewPager
    private lateinit var indicator: PageIndicator
    private val adapter = CardPagerAdapter(context)
    private var scrollState = ViewPager.SCROLL_STATE_IDLE
    private var pendingTargets: List<SmartspaceTarget>? = null
    private var runningAnimation: Animator? = null
    private var style = SmartspaceStyle()

    override fun onFinishInflate() {
        super.onFinishInflate()
        viewPager = findViewById<ViewPager>(R.id.smartspace_card_pager)!!
        viewPager.isSaveEnabled = false
        indicator = findViewById<PageIndicator>(R.id.smartspace_page_indicator)!!

        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int,
            ) {
                indicator.setPageOffset(position, positionOffset)
            }

            override fun onPageSelected(position: Int) {
            }

            override fun onPageScrollStateChanged(state: Int) {
                scrollState = state
                if (state == 0) {
                    pendingTargets?.let {
                        pendingTargets = null
                        onSmartspaceTargetsUpdate(it)
                    }
                }
            }
        })

        val targets = if (previewMode) provider.previewTargets else provider.targets
        val styles = SmartspaceStyle.flow(context, PreferenceManager2.getInstance(context))
        repeatOnAttached {
            viewPager.adapter = adapter
            combine(targets, styles) { items, itemStyle -> items to itemStyle }
                .onEach { (items, newStyle) ->
                    applyStyle(newStyle)
                    onSmartspaceTargetsUpdate(items)
                }
                .launchIn(this)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val smartspaceHeight =
            context.resources.getDimensionPixelSize(R.dimen.enhanced_smartspace_height)
        if (height <= 0 || height >= smartspaceHeight) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            scaleX = 1f
            scaleY = 1f
            return
        }

        val scale = height.toFloat() / smartspaceHeight.toFloat()
        val width = (MeasureSpec.getSize(widthMeasureSpec).toFloat() / scale).roundToInt()
        super.onMeasure(
            makeMeasureSpec(width, EXACTLY),
            makeMeasureSpec(smartspaceHeight, EXACTLY),
        )
        scaleX = scale
        scaleY = scale
        pivotX = 0f
        pivotY = smartspaceHeight.toFloat() / 2f
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        viewPager.setOnLongClickListener(l)
    }

    private fun applyStyle(newStyle: SmartspaceStyle) {
        style = newStyle
        adapter.style = newStyle
        indicator.dotsAllowed = !newStyle.oneCard && newStyle.pageDots
    }

    private fun onSmartspaceTargetsUpdate(targets: List<SmartspaceTarget>) {
        if (adapter.count > 1 && scrollState != ViewPager.SCROLL_STATE_IDLE) {
            pendingTargets = targets
            return
        }

        if (style.oneCard) {
            val sorted = targets.sortedByDescending { it.score }
            val primary = sorted.firstOrNull { it.featureType == SmartspaceTarget.FeatureType.FEATURE_WEATHER }
                ?: sorted.firstOrNull()
            val extras = sorted.filter { it !== primary && it.featureType != SmartspaceTarget.FeatureType.FEATURE_WEATHER }
            adapter.setTargets(listOfNotNull(primary), extras)
            indicator.setNumPages(1)
            if (viewPager.currentItem != 0) viewPager.setCurrentItem(0, false)
            return
        }

        // What needs to be seen now (the flashlight, music, an alarm due soon) comes first, and the
        // pager turns to it by itself when it appears.
        val urgent = GlanceRanking.urgentItems(targets, System.currentTimeMillis())
        val sortedTargets = (urgent + targets.filter { it !in urgent }.sortedByDescending { it.score }).toMutableList()
        val urgentKey = urgent.firstOrNull()?.id
        val showUrgent = urgentKey != null && urgentKey != lastUrgentKey
        lastUrgentKey = urgentKey
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val currentItem = viewPager.currentItem
        val index = if (isRtl) adapter.count - currentItem else currentItem
        if (isRtl) {
            sortedTargets.reverse()
        }

        val oldCard = adapter.getCardAtPosition(currentItem)
        adapter.setTargets(sortedTargets)
        val count = adapter.count
        if (isRtl) {
            viewPager.setCurrentItem((count - index).coerceIn(0 until count), false)
        }
        indicator.setNumPages(targets.size)
        oldCard?.let { animateSmartspaceUpdate(it) }
        adapter.notifyDataSetChanged()
        if (showUrgent) {
            val first = if (isRtl) count - 1 else 0
            if (viewPager.currentItem != first) viewPager.setCurrentItem(first, true)
        }
    }

    private var lastUrgentKey: String? = null

    private fun animateSmartspaceUpdate(oldCard: BcSmartspaceCard) {
        if (runningAnimation != null || oldCard.parent != null) return

        val animParent = viewPager.parent as ViewGroup
        oldCard.measure(makeMeasureSpec(viewPager.width, EXACTLY), makeMeasureSpec(viewPager.height, EXACTLY))
        oldCard.layout(viewPager.left, viewPager.top, viewPager.right, viewPager.bottom)
        val shift = resources.getDimension(R.dimen.enhanced_smartspace_dismiss_margin)
        val animator = AnimatorSet()
        animator.play(
            ObjectAnimator.ofFloat(
                oldCard,
                View.TRANSLATION_Y,
                0f,
                (-height).toFloat() - shift,
            ),
        )
        animator.play(ObjectAnimator.ofFloat(oldCard, View.ALPHA, 1f, 0f))
        animator.play(
            ObjectAnimator.ofFloat(
                viewPager,
                View.TRANSLATION_Y,
                height.toFloat() + shift,
                0f,
            ),
        )
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                animParent.overlay.add(oldCard)
            }

            override fun onAnimationEnd(animation: Animator) {
                animParent.overlay.remove(oldCard)
                runningAnimation = null
            }
        })
        runningAnimation = animator
        animator.start()
    }
}
