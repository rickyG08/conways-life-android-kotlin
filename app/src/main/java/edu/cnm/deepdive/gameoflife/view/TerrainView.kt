/*
 *  Copyright 2021 CNM Ingenuity, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package edu.cnm.deepdive.gameoflife.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import edu.cnm.deepdive.gameoflife.model.Terrain
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.jvm.Volatile
import kotlin.math.max

class TerrainView : View {

    private val source: Rect
    private val dest: Rect
    private val cellColors: IntArray
    private val scheduler: ScheduledExecutorService
    private val updater: DisplayUpdater
    private var bitmap: Bitmap? = null
    private lateinit var cells: Array<ByteArray>
    private var future: ScheduledFuture<*>?
    private var background: Int
    private var colorsUpdated: Boolean

    private var _terrain: Terrain?
    var terrain: Terrain?
        get() = _terrain
        set(terrain: Terrain?) {
            bitmap = null
            _terrain = terrain?.also {
                val size = it.size
                cells = Array(size) { ByteArray(size) }
                bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
                source[0, 0, size] = size
            }
            colorsUpdated = false
        }

    private var _hue: Float
    var hue: Float
        get() = _hue
        set(hue: Float) {
            _hue = hue % MAX_HUE
            if (_hue < 0) {
                _hue += MAX_HUE
            }
            colorsUpdated = false
        }

    private var _saturation: Float
    var saturation: Float
        get() = _saturation
        set(saturation: Float) {
            _saturation = MAX_SATURATION.coerceAtMost(0f.coerceAtLeast(saturation))
            colorsUpdated = false
        }

    private var _newBrightness: Float
    var newBrightness: Float
        get() = _newBrightness
        set(newBrightness: Float) {
            _newBrightness = MAX_BRIGHTNESS.coerceAtMost(0f.coerceAtLeast(newBrightness))
            colorsUpdated = false
        }

    private var _oldBrightness: Float
    var oldBrightness: Float
        get() = _oldBrightness
        set(oldBrightness: Float) {
            _oldBrightness = MAX_BRIGHTNESS.coerceAtMost(0f.coerceAtLeast(oldBrightness))
            colorsUpdated = false
        }

    var generation: Long
        get() = updater.generation
        set(generation: Long) {
            updater.generation = generation
        }

    init {
        setWillNotDraw(false)
        source = Rect()
        dest = Rect()
        cellColors = IntArray(Byte.MAX_VALUE.toInt())
        background = Color.BLACK
        scheduler = Executors.newScheduledThreadPool(1)
        updater = DisplayUpdater()
        future = null
        _terrain = null
        _hue = DEFAULT_HUE
        _saturation = DEFAULT_SATURATION
        _newBrightness = DEFAULT_NEW_BRIGHTNESS
        _oldBrightness = DEFAULT_OLD_BRIGHTNESS
        colorsUpdated = false
    }

    constructor(context: Context?) : super(context) {}

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int,
                defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {}

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = suggestedMinimumWidth
        var height = suggestedMinimumHeight
        width = resolveSizeAndState(paddingLeft + paddingRight + width, widthMeasureSpec, 0)
        height = resolveSizeAndState(paddingTop + paddingBottom + height, heightMeasureSpec, 0)
        val size = max(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateBitmap()
    }

    override fun onDraw(canvas: Canvas) {
        bitmap?.let {
            dest[0, 0, width] = height
            canvas.drawBitmap(it, source, dest, null)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        stopDisplayUpdates()
        future = scheduler.scheduleWithFixedDelay(
                updater, UPDATE_INTERVAL, UPDATE_INTERVAL, TimeUnit.MILLISECONDS)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopDisplayUpdates()
    }

    private fun updateBitmap() {
        bitmap?.let { bits ->
            if (!colorsUpdated) {
                updateColors()
            }
            _terrain?.let { terr ->
                terr.copyCells(cells)
                for (rowIndex in cells.indices) {
                    for (colIndex in cells[rowIndex].indices) {
                        val age = cells[rowIndex][colIndex]
                        bits.setPixel(colIndex, rowIndex, if (age > 0) cellColors[age - 1] else background)
                    }
                }
            }
        }
    }

    private fun updateColors() {
        for (i in 0 until Byte.MAX_VALUE) {
            val brightness = _oldBrightness +
                    (_newBrightness - _oldBrightness) * (Byte.MAX_VALUE - i) / Byte.MAX_VALUE
            cellColors[i] = Color.HSVToColor(floatArrayOf(_hue, _saturation, brightness))
        }
        colorsUpdated = true
    }

    private fun stopDisplayUpdates() {
        if (future != null) {
            future!!.cancel(true)
        }
    }

    private inner class DisplayUpdater : Runnable {

        @Volatile
        var generation: Long = 0

        private var lastGeneration: Long = 0

        override fun run() {
            if (generation == 0L || generation > lastGeneration) {
                lastGeneration = generation
                updateBitmap()
                if (generation == 0L) {
                    postInvalidate()
                }
            }
        }
    }

    companion object {
        private const val MAX_HUE = 360f
        private const val MAX_SATURATION = 1f
        private const val MAX_BRIGHTNESS = 1f
        private const val DEFAULT_HUE = 300f
        private const val DEFAULT_SATURATION = 1f
        private const val DEFAULT_NEW_BRIGHTNESS = 1f
        private const val DEFAULT_OLD_BRIGHTNESS = 0.6f
        private const val UPDATE_INTERVAL = 20L
    }

}