package com.github.lixlee.dsbridge.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.lixlee.dsbridge.app.web.WebPageFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val webPageFragment = supportFragmentManager.findFragmentByTag("WebPageFragment") as? WebPageFragment
        webPageFragment?.loadUrl("https://www.bing.com/")
    }
}