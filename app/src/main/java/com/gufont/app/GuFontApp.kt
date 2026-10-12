package com.gufont.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class GuFontApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // PDFBox 在 Android 上必须先初始化资源加载器
        PDFBoxResourceLoader.init(applicationContext)
    }
}
