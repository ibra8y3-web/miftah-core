package com.miftah.core.app

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Real launcher icons from PackageManager, loaded lazily and cached. A letter badge is shown if none can be read. */
private object IconCache {
    private val cache = LruCache<String, ImageBitmap>(400)
    fun get(pkg: String): ImageBitmap? = cache.get(pkg)
    fun put(pkg: String, bmp: ImageBitmap) { cache.put(pkg, bmp) }
}

private fun loadIcon(context: Context, pkg: String): ImageBitmap? = try {
    context.packageManager.getApplicationIcon(pkg).toBitmap(112, 112).asImageBitmap()
} catch (e: PackageManager.NameNotFoundException) {
    null
} catch (e: RuntimeException) {
    null
}

@Composable
internal fun AppIcon(pkg: String, label: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = IconCache.get(pkg), pkg) {
        if (value == null) {
            try {
                value = withContext(Dispatchers.IO) { loadIcon(context, pkg)?.also { IconCache.put(pkg, it) } }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = modifier.size(size).clip(RoundedCornerShape(size / 4)))
    } else {
        Box(
            modifier.size(size).clip(RoundedCornerShape(size / 4)).then(Modifier),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(size)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}
