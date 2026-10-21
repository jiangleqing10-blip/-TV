package com.muqitv.tvapp

/**
 * 所有需要改的配置都集中在这一个文件里，方便以后换服务器/改激活码时
 * 只改这一处、重新用 GitHub Actions 编译一次即可。
 */
object Constants {
    /** 网站地址（必须是 https，WebView 默认禁用明文 http 访问） */
    const val SERVER_URL = "https://muqitv.com"

    /** 网站的访问密码（即服务器 docker run 时 -e PASSWORD= 设置的那个值） */
    const val SITE_PASSWORD = "20250415"

    /**
     * App 自己的激活码：首次打开 App 时要求输入一次，验证通过后写入本地存储，
     * 以后不再弹出（即便 U 盘拔掉、恢复出厂设置以外都不会再问）。
     * 建议和网站密码区分开，更安全；在安装前可以改成你自己想要的激活码。
     */
    const val ACTIVATION_CODE = "MUQI20250415"
}
