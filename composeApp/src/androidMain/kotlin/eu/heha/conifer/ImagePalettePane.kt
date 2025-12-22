package eu.heha.conifer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.palette.graphics.Palette
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext


@Suppress("unused")
@Composable
fun ImagePalettePane(imageResources: List<Int>) {
    var selectedColorHex by remember { mutableStateOf<String?>(null) }
    Scaffold { innerPadding ->
        Box {
            LazyColumn(Modifier.padding(innerPadding)) {
                items(imageResources) { imageResource ->
                    Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        ImageWithSwatches(
                            resId = imageResource,
                            onClickSwatch = { swatch ->
                                val colorHex = swatch.rgb.toHexString().drop(2)
                                Napier.i { "Clicked color swatch: #$colorHex" }
                                selectedColorHex = colorHex
                            }
                        )
                    }
                }

                item {
                    val height = if (selectedColorHex == null) 16.dp else 88.dp
                    Spacer(Modifier.height(height))
                }
            }

            AnimatedVisibility(selectedColorHex != null, Modifier.align(Alignment.BottomCenter)) {
                Surface(Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        ColorDot(Color("#${selectedColorHex}".toColorInt()))
                        Spacer(Modifier.size(8.dp))
                        SelectionContainer {
                            Text(
                                text = "Selected color: #${selectedColorHex}",
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageWithSwatches(
    @RawRes resId: Int,
    onClickSwatch: (Palette.Swatch) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        var image: Bitmap? by remember { mutableStateOf(null) }
        var swatches: List<Palette.Swatch> by remember { mutableStateOf(emptyList()) }
        LaunchedEffect(resId) {
            val loadedBitmap = loadBitmap(context, resId)
            image = loadedBitmap
            swatches = loadColorPalettes(loadedBitmap)
        }
        AnimatedContent(image, Modifier.align(Alignment.CenterHorizontally)) {
            if (it == null) {
                CircularProgressIndicator(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp)
                )
            } else {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
            }
        }

        AnimatedContent(swatches) { swatches ->
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                swatches.forEach { swatch ->
                    IconButton({ onClickSwatch(swatch) }) {
                        ColorDot(Color(swatch.rgb))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorDot(color: Color) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .padding(2.dp)
    )
}

private suspend fun loadBitmap(context: Context, @RawRes resId: Int): Bitmap = withContext(IO) {
    context.resources.openRawResource(resId).use {
        BitmapFactory.decodeStream(it)
    }
}

private suspend fun loadColorPalettes(bitmap: Bitmap) = withContext(IO) {
    val palette = Palette.from(bitmap).generate()
    Napier.e { "swatches for image pollen cones" }
    palette.swatches
}