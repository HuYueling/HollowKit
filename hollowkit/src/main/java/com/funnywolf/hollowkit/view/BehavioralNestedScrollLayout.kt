package com.funnywolf.hollowkit.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Scroller
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import com.funnywolf.hollowkit.utils.*

/**
 * 嵌套滑动布局
 *
 * @author https://github.com/funnywolfdadada
 * @since 2020/5/8
 */
open class BehavioralNestedScrollLayout : FrameLayout, NestedScrollingParent3 {

    private var behavior: NestedScrollBehavior? = null

    private val views = arrayOfNulls<View>(3)
    private val targets = arrayOfNulls<NestedScrollTarget>(3)

    /**
     * 滚动的最小值
     */
    var minScroll = 0
        private set
    var minOverScroll = 0
        private set

    /**
     * 滚动的最大值
     */
    var maxScroll = 0
        private set
    var maxOverScroll = 0
        private set

    /**
     * 上次触摸事件的 y 值，用于处理自身的滑动事件
     */
    private var lastPosition = 0

    private var nestedScrollChild: View? = null
    private var nestedScrollTarget: View? = null

    /**
     * 用来处理松手时的连续滚动
     */
    private val scroller = Scroller(context)

    /**
     * 用于计算抬手时的速度
     */
    private val velocityTracker by lazy { VelocityTracker.obtain() }

    private val parentHelper by lazy { NestedScrollingParentHelper(this) }

    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet?): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    fun setupBehavior(behavior: NestedScrollBehavior?) {
        removeAllViews()
        this.behavior = behavior
        views[0] = behavior?.prevView?.also {
            addView(it)
        }
        targets[0] = behavior?.prevScrollTarget
        views[1] = behavior?.contentView?.also {
            addView(it)
        }
        targets[1] = behavior?.contentScrollTarget
        views[2] = behavior?.nextView?.also {
            addView(it)
        }
        targets[2] = behavior?.nextScrollTarget
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        minScroll = 0
        maxScroll = 0
        layoutVertical(left, top, right, bottom)
        minOverScroll = minScroll + (behavior?.minOverScrollOffset ?: 0)
        maxOverScroll = maxScroll + (behavior?.maxOverScrollOffset ?: 0)
    }

    private fun layoutVertical(left: Int, top: Int, right: Int, bottom: Int) {
        var t = top
        views[0]?.also {
            it.layout(
                left + ((it.layoutParams as? MarginLayoutParams)?.leftMargin ?: 0),
                t - it.measuredHeight,
                right + ((it.layoutParams as? MarginLayoutParams)?.rightMargin ?: 0),
                0
            )
            minScroll = -it.measuredHeight
        }
        views[1]?.also {
            it.layout(
                left + ((it.layoutParams as? MarginLayoutParams)?.leftMargin ?: 0),
                t,
                right + ((it.layoutParams as? MarginLayoutParams)?.rightMargin ?: 0),
                t + it.measuredHeight
            )
            t += it.measuredHeight
        }
        views[2]?.also {
            it.layout(
                left + ((it.layoutParams as? MarginLayoutParams)?.leftMargin ?: 0),
                t,
                right + ((it.layoutParams as? MarginLayoutParams)?.rightMargin ?: 0),
                t + it.measuredHeight
            )
            maxScroll = (it.y + it.height - height).toInt()
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        return when (e.action) {
            // down 时不拦截，但需要记录位置
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                lastPosition = e.y.toInt()
                nestedScrollChild = findChildUnder(e.rawX, e.rawY)
                nestedScrollTarget = findVerticalNestedScrollingTarget(e.rawX, e.rawY)
                false
            }
            // move 时需要根据是否移动，是否有可处理对应方向移动的子 view，判断是否要自己拦截
            MotionEvent.ACTION_MOVE -> {
                if (lastPosition != e.y.toInt() && nestedScrollTarget == null) {
                    true
                } else {
                    lastPosition = e.y.toInt()
                    false
                }
            }
            else -> super.onInterceptTouchEvent(e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        return when (e.action) {
            // down 不处理，之后的就无法处理
            MotionEvent.ACTION_DOWN -> {
                velocityTracker.addMovement(e)
                // 请求父 view 不要拦截事件
                requestDisallowInterceptTouchEvent(true)
                true
            }
            // move 时判断自身是否能够处理
            MotionEvent.ACTION_MOVE -> {
                velocityTracker.addMovement(e)
                val dy = (lastPosition - e.y).toInt()
                lastPosition = e.y.toInt()
                dispatchScroll(dy, ViewCompat.TYPE_TOUCH)
                true
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker.addMovement(e)
                velocityTracker.computeCurrentVelocity(1000)
                fling(-velocityTracker.yVelocity.toInt())
                velocityTracker.clear()
                true
            }
            else -> super.onTouchEvent(e)
        }
    }

    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
        return (axes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        parentHelper.onNestedScrollAccepted(child, target, axes, type)
        nestedScrollChild = child
        nestedScrollTarget = target
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        if (dispatchScroll(dy, type)) {
            consumed[1] = dy
        }
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        dispatchScroll(dyUnconsumed, type)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int
    ) {
        dispatchScroll(dyUnconsumed, type)
    }

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        val handled = super.onNestedPreFling(target, velocityX, velocityY)
        if (!handled) {
            fling(velocityY.toInt())
        }
        return true
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        parentHelper.onStopNestedScroll(target, type)
    }

    private fun fling(v: Int) {
        lastPosition = 0
        scroller.fling(0, lastPosition, 0, v, 0, 0, Int.MIN_VALUE, Int.MAX_VALUE)
        invalidate()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val currY = scroller.currY
            val scroll = currY - lastPosition
            Log.d(javaClass.simpleName, "computeScroll $scroll")
            if (!dispatchScroll(scroll, ViewCompat.TYPE_NON_TOUCH)) {
                nestedScrollTarget?.scrollBy(0, scroll)
            }
            lastPosition = currY
            invalidate()
        }
    }

    /**
     * 分发滚动量
     * @return 是否自己处理滚动量
     */
    private fun dispatchScroll(scroll: Int, @ViewCompat.NestedScrollType type: Int): Boolean {
        Log.d(javaClass.simpleName, "dispatchScroll $scroll ${type == ViewCompat.TYPE_TOUCH} ${nestedScrollTarget?.canScrollVertically(scroll)}")
        return when {
            // 默认 0 可以处理
            scroll == 0 -> true
            // 优先自己滚动且自己可以滚动，就自己滚动
            scrollSelfFirst(scroll, type) && canScrollSelf(scroll) -> {
                scrollSelf(scroll, type)
                true
            }
            // 判断 nestedScrollTarget 是否滚动
            shouldTargetScroll(scroll, type) -> false
            // nestedScrollTarget 不滚动就自己滚动
            else -> {
                switchTargetIfNeed(scroll, type)
                scrollSelf(scroll, type)
                true
            }
        }
    }

    private fun scrollSelfFirst(scroll: Int, @ViewCompat.NestedScrollType type: Int): Boolean {
        return behavior?.scrollSelfFirst?.invoke(this, scroll, type) ?: when (behavior?.scrollVertical) {
            true -> (scrollY in minOverScroll until minScroll
                    || scrollY in (maxScroll + 1)..maxOverScroll)
            false -> (scrollX in minOverScroll until minScroll
                    || scrollX in (maxScroll + 1)..maxOverScroll)
            else -> false
        }
    }

    private fun shouldTargetScroll(scroll: Int, @ViewCompat.NestedScrollType type: Int): Boolean {
        return nestedScrollTarget?.canScrollVertically(scroll) == true
                && nestedScrollChild?.let { isChildTotallyShowing(it) } == true
    }

    private fun isChildTotallyShowing(c: View): Boolean {
        val relativeY = c.y - scrollY
        return relativeY >= 0 && relativeY + c.height <= height
    }

    private fun switchTargetIfNeed(scroll: Int, @ViewCompat.NestedScrollType type: Int) {
        if (ViewCompat.TYPE_NON_TOUCH == type && nestedScrollTarget?.canScrollVertically(scroll) != true) {
            val child = nestedScrollChild ?: return
            val index = views.indexOf(child) + if (scroll > 0) { 1 } else { -1 }
            if (index >= 0 && index < views.size) {
                nestedScrollChild = views[index]
                nestedScrollTarget = targets[index]?.invoke(this, scroll, type)
                    ?: (nestedScrollChild as? ViewGroup)?.findVerticalScrollableTarget(scroll, true)
            }
        }
    }

    private fun scrollSelf(scroll: Int, @ViewCompat.NestedScrollType type: Int) {
        scrollBy(0, scroll)
    }

    private fun canScrollSelf(dir: Int): Boolean {
        return when (behavior?.scrollVertical) {
            true -> canScrollVertically(dir)
            false -> canScrollHorizontally(dir)
            else -> false
        }
    }

    override fun canScrollVertically(direction: Int): Boolean {
        return when {
            behavior?.scrollVertical != true -> false
            direction > 0 -> scrollY < maxOverScroll
            direction < 0 -> scrollY > minOverScroll
            else -> true
        }
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        return when {
            behavior?.scrollVertical != false -> false
            direction > 0 -> scrollX < maxOverScroll
            direction < 0 -> scrollX > minOverScroll
            else -> true
        }
    }

    override fun scrollBy(x: Int, y: Int) {
        super.scrollBy(getScrollByX(x), getScrollByY(y))
    }

    private fun getScrollByX(dx: Int): Int {
        val newX = scrollX + dx
        return when {
            behavior?.scrollVertical != false -> scrollX
            scrollX > 0 -> when {
                scrollX > maxScroll -> newX.constrains(maxScroll, maxOverScroll)
                scrollX < maxScroll -> newX.constrains(0, maxScroll)
                else -> newX.constrains(0, maxOverScroll)
            }
            scrollX < 0 -> when {
                scrollX > minScroll -> newX.constrains(minScroll, 0)
                scrollX < minScroll -> newX.constrains(minOverScroll, minScroll)
                else -> newX.constrains(minOverScroll, 0)
            }
            else -> newX.constrains(minScroll, maxScroll)
        } - scrollX
    }

    private fun getScrollByY(dy: Int): Int {
        val newY = scrollY + dy
        return when {
            behavior?.scrollVertical != true -> scrollY
            scrollY > 0 -> when {
                scrollY > maxScroll -> newY.constrains(maxScroll, maxOverScroll)
                scrollY < maxScroll -> newY.constrains(0, maxScroll)
                else -> newY.constrains(0, maxOverScroll)
            }
            scrollY < 0 -> when {
                scrollY > minScroll -> newY.constrains(minScroll, 0)
                scrollY < minScroll -> newY.constrains(minOverScroll, minScroll)
                else -> newY.constrains(minOverScroll, 0)
            }
            else -> newY.constrains(minScroll, maxScroll)
        } - scrollY
    }

    private fun Int.constrains(min: Int, max: Int): Int = when {
        this < min -> min
        this > max -> max
        else -> this
    }

}

/**
 * 嵌套滚动的目标 View
 * 参数分别时 [BehavioralNestedScrollLayout]、滚动量和 [ViewCompat.NestedScrollType]
 */
typealias NestedScrollTarget = (BehavioralNestedScrollLayout, Int, Int)->View

class NestedScrollBehavior(
    val contentView: View,
    val contentScrollTarget: NestedScrollTarget? = null
) {
    var prevView: View? = null
    var prevScrollTarget: NestedScrollTarget? = null

    var nextView: View? = null
    var nextScrollTarget: NestedScrollTarget? = null

    var scrollVertical: Boolean = true

    var minOverScrollOffset: Int = 0
    var maxOverScrollOffset: Int = 0

    /**
     * 是否优先滚动自己
     * 参数分别时 [BehavioralNestedScrollLayout]、滚动量和 [ViewCompat.NestedScrollType]
     */
    var scrollSelfFirst: ((BehavioralNestedScrollLayout, Int, Int)->Boolean)? = null

    fun setPrevView(v: View, target: NestedScrollTarget? = null): NestedScrollBehavior {
        prevView = v
        prevScrollTarget = target
        return this
    }

    fun setNextView(v: View, target: NestedScrollTarget? = null): NestedScrollBehavior {
        nextView = v
        nextScrollTarget = target
        return this
    }

    fun setOrientation(vertical: Boolean): NestedScrollBehavior {
        this.scrollVertical = vertical
        return this
    }

    fun setOverScrollOffset(min: Int, max: Int): NestedScrollBehavior {
        minOverScrollOffset = min
        maxOverScrollOffset = max
        return this
    }

}
