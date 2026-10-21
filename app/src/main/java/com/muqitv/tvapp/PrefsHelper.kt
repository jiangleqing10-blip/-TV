package com.muqitv.tvapp

import android.content.Context

/** 本地激活状态存储：写在系统应用私有目录下，和网站/浏览器的数据完全独立，
 *  拔掉安装用的 U 盘、清理浏览器缓存都不会影响这个标记。 */
object PrefsHelper {
    private const val PREFS_NAME = "muqitv_prefs"
    private const val KEY_ACTIVATED = "activated"

    fun isActivated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ACTIVATED, false)
    }

    fun setActivated(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ACTIVATED, true).apply()
    }
}
