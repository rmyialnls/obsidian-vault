package com.tracksnatcher.app.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tracksnatcher.app.BuildConfig
import com.tracksnatcher.app.domain.model.SonicMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Renders [MemoryCard] into an offscreen [androidx.compose.ui.graphics.layer.GraphicsLayer],
 * and on tap captures it to a Bitmap, writes it to a FileProvider-shared cache file, and
 * fires the native share sheet (Instagram Stories, etc.) via [Intent.ACTION_SEND].
 */
@Composable
fun ShareableMemoryCard(memory: SonicMemory, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MemoryCard(
            memory = memory,
            modifier = Modifier.drawWithContent {
                graphicsLayer.record { this@drawWithContent.drawContent() }
                drawLayer(graphicsLayer)
            },
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                    val uri = saveForShare(context, bitmap, memory.id)
                    shareImage(context, uri)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Share Memory")
        }
    }
}

private suspend fun saveForShare(context: Context, bitmap: Bitmap, id: String): Uri =
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(dir, "memory_$id.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
    }

private fun shareImage(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share your Sonic Memory"))
}
