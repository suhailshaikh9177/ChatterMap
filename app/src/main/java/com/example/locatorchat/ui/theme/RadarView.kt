package com.example.locatorchat.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.locatorchat.model.DiscoveredUser
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // Listener to notify the activity when a user is clicked
    interface OnUserClickListener {
        fun onUserClick(user: DiscoveredUser)
    }
    var userClickListener: OnUserClickListener? = null

    // --- Paint Objects ---
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(100, 0, 255, 0)
        strokeWidth = 3f
    }
    private val radarSweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.GREEN
        strokeWidth = 5f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.CYAN
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    // --- Animation and Data ---
    private val handler = Handler(Looper.getMainLooper())
    private var sweepAngle = 0f
    private val discoveredUsers = mutableMapOf<String, Pair<DiscoveredUser, PointF>>() // Key: endpointId

    private val sweepRunnable = object : Runnable {
        override fun run() {
            sweepAngle = (sweepAngle + 2f) % 360
            invalidate()
            handler.postDelayed(this, 20)
        }
    }

    fun startAnimation() {
        handler.post(sweepRunnable)
    }

    fun stopAnimation() {
        handler.removeCallbacks(sweepRunnable)
    }

    fun addUser(user: DiscoveredUser) {
        if (!discoveredUsers.containsKey(user.endpointId)) {
            val centerX = width / 2f
            val centerY = height / 2f
            val maxRadius = min(centerX, centerY) * 0.8f

            val angle = (0..360).random().toDouble()
            val radius = (maxRadius * 0.3 + (maxRadius * 0.6 * Math.random())).toFloat()

            val x = (centerX + radius * cos(Math.toRadians(angle))).toFloat()
            val y = (centerY + radius * sin(Math.toRadians(angle))).toFloat()
            discoveredUsers[user.endpointId] = Pair(user, PointF(x, y))
            invalidate()
        }
    }

    fun removeUser(endpointId: String) {
        if (discoveredUsers.containsKey(endpointId)) {
            discoveredUsers.remove(endpointId)
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        if (centerX == 0f || centerY == 0f) return

        val maxRadius = min(centerX, centerY) - 20f

        // Draw concentric circles
        canvas.drawCircle(centerX, centerY, maxRadius * 0.25f, circlePaint)
        canvas.drawCircle(centerX, centerY, maxRadius * 0.50f, circlePaint)
        canvas.drawCircle(centerX, centerY, maxRadius * 0.75f, circlePaint)
        canvas.drawCircle(centerX, centerY, maxRadius, circlePaint)

        // Draw radar sweep
        canvas.drawLine(
            centerX, centerY,
            (centerX + maxRadius * cos(Math.toRadians(sweepAngle.toDouble()))).toFloat(),
            (centerY + maxRadius * sin(Math.toRadians(sweepAngle.toDouble()))).toFloat(),
            radarSweepPaint
        )

        // Draw discovered users (dot and username)
        discoveredUsers.values.forEach { (user, point) ->
            canvas.drawCircle(point.x, point.y, 20f, dotPaint)
            canvas.drawText(user.username, point.x, point.y - 30f, textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val clickRadius = 60f // Increased touch area
            for ((user, point) in discoveredUsers.values) {
                val distance = sqrt((event.x - point.x).pow(2) + (event.y - point.y).pow(2))
                if (distance < clickRadius) {
                    userClickListener?.onUserClick(user)
                    return true // Event handled
                }
            }
        }
        return super.onTouchEvent(event)
    }
}