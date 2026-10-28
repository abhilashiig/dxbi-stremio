package com.abhilash.matinee.tv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.abhilash.matinee.tv.data.Http
import com.abhilash.matinee.tv.data.Library
import com.abhilash.matinee.tv.data.Repo
import com.abhilash.matinee.tv.data.Session
import com.abhilash.matinee.tv.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toOkioPath

class MatineeApp : Application(), SingletonImageLoader.Factory {
    companion object {
        // Outlives screens and the player, so progress saves finish after they close
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onCreate() {
        super.onCreate()
        Http.init(this)
        Session.init(this)
        Settings.init(this)
        Library.init(this)
        Repo.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { Http.client })) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
        .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("images").toOkioPath()).maxSizeBytes(256L * 1024 * 1024).build() }
        .crossfade(180)
        .build()
}
