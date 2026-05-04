/*
 * This file is part of LightEmail.
 *
 * LightEmail is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * LightEmail is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LightEmail.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2018-2020, Distopico (dystopia project) <distopico@riseup.net> and contributors
 */
package org.dystopia.email.util

import android.content.Context

object ViewHelper {
    /**
     * Convert density-independent pixels units to pixel units.
     *
     * @param context - android content context to get density
     * @param dp      - density-independent pixel value
     */
    @JvmStatic
    fun dp2px(context: Context, dp: Int): Int {
        val scale = context.resources.displayMetrics.density
        return Math.round(dp * scale)
    }

    /**
     * Convert pixel units to density-independent pixels units.
     *
     * @param context - android content context to get density
     * @param px      - pixels value
     */
    @JvmStatic
    fun px2dp(context: Context, px: Float): Int {
        val scale = context.resources.displayMetrics.density
        return Math.round(px / scale)
    }
}
