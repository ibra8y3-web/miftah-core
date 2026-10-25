package com.miftah.core.app

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores real launcher icons obtained from PackageManager.
 * Icons are loaded lazily and cached to avoid repeated work.
 */
private object IconCache {
    private val cache = LruCache<String, ImageBitmap>(400)

    fun get(pkg: String): ImageBitmap? = cache.get(pkg)

    fun put(pkg: String, bitmap: ImageBitmap) {
        cache.put(pkg, bitmap)
    }
}

/**
 * Loads the actual application icon installed on this device.
 * Returns null if the package cannot be found or the icon cannot be read.
 */
private fun loadIcon(
    context: Context,
    pkg: String
): ImageBitmap? {
    return try {
        context.packageManager
            .getApplicationIcon(pkg)
            .toBitmap(112, 112)
            .asImageBitmap()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: RuntimeException) {
        null
    }
}

/**
 * Displays the real application icon.
 * Shows the first letter of the application label if no icon is available.
 */
@Composable
internal fun AppIcon(
    pkg: String,
    label: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Reuse a cached icon immediately when available.
    val bitmapState = remember(pkg) {
        mutableStateOf(IconCache.get(pkg))
    }

    // Load missing icons asynchronously.
    // This avoids the ProduceStateDoesNotAssignValue lint error.
    LaunchedEffect(pkg, context) {
        if (bitmapState.value == null) {
            val loadedBitmap = withContext(Dispatchers.IO) {
                loadIcon(context, pkg)?.also { bitmap ->
                    IconCache.put(pkg, bitmap)
                }
            }

            bitmapState.value = loadedBitmap
        }
    }

    val bitmap = bitmapState.value

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(size)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}