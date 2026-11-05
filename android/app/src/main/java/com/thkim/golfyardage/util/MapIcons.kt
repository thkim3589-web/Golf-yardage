package com.thkim.golfyardage.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/** 벡터 드로어블을 구글 지도 마커 아이콘(BitmapDescriptor)으로 변환한다. */
object MapIcons {

    private val cache = mutableMapOf<Int, BitmapDescriptor>()

    fun from(context: Context, drawableRes: Int): BitmapDescriptor {
        cache[drawableRes]?.let { return it }

        val drawable = ContextCompat.getDrawable(context, drawableRes)
            ?: return BitmapDescriptorFactory.defaultMarker()
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
        cache[drawableRes] = descriptor
        return descriptor
    }
}
