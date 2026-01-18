package com.wetzone.mp3playerboom

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SparseBooleanArray
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : Activity() {

    private val _timer = Timer()
    private var linear1: LinearLayout? = null
    private var imageview2: ImageView? = null
    private var t: TimerTask? = null
    private val go = Intent()

    override fun onCreate(_savedInstanceState: Bundle?) {
        super.onCreate(_savedInstanceState)
        setContentView(R.layout.main)
        initialize(_savedInstanceState)
        initializeLogic()
    }

    private fun initialize(_savedInstanceState: Bundle?) {
        linear1 = findViewById(R.id.linear1)
        imageview2 = findViewById(R.id.imageview2)
    }

    private fun initializeLogic() {
        t = object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    // StartActivity must be defined in the project or manifest
//                    go.setClass(applicationContext, StartActivity::class.java)
                    go.setClass(applicationContext, RecordWhilePlaying::class.java)
                    startActivity(go)
                    finish()
                }
            }
        }
        _timer.schedule(t, 1500L)
    }

    @Deprecated("Use Toast directly")
    fun showMessage(_s: String) {
        Toast.makeText(applicationContext, _s, Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Use View.getLocationInWindow")
    fun getLocationX(_v: View): Int {
        val _location = IntArray(2)
        _v.getLocationInWindow(_location)
        return _location[0]
    }

    @Deprecated("Use View.getLocationInWindow")
    fun getLocationY(_v: View): Int {
        val _location = IntArray(2)
        _v.getLocationInWindow(_location)
        return _location[1]
    }

    @Deprecated("Use Random.nextInt")
    fun getRandom(_min: Int, _max: Int): Int {
        val random = Random()
        return random.nextInt(_max - _min + 1) + _min
    }

    @Deprecated("Use ListView.checkedItemPositions")
    fun getCheckedItemPositionsToArray(_list: ListView): ArrayList<Double> {
        val _result = ArrayList<Double>()
        val _arr = _list.checkedItemPositions
        for (_iIdx in 0 until _arr.size()) {
            if (_arr.valueAt(_iIdx)) {
                _result.add(_arr.keyAt(_iIdx).toDouble())
            }
        }
        return _result
    }

    @Deprecated("Use TypedValue.applyDimension")
    fun getDip(_input: Int): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            _input.toFloat(),
            resources.displayMetrics
        )
    }

    @Deprecated("Use resources.displayMetrics.widthPixels")
    fun getDisplayWidthPixels(): Int {
        return resources.displayMetrics.widthPixels
    }

    @Deprecated("Use resources.displayMetrics.heightPixels")
    fun getDisplayHeightPixels(): Int {
        return resources.displayMetrics.heightPixels
    }
}
