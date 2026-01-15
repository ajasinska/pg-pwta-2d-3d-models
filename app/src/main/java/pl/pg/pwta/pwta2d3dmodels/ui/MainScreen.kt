package pl.pg.pwta.pwta2d3dmodels.ui

import pl.pg.pwta.pwta2d3dmodels.model.*
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.Composable
import io.github.sceneview.SceneView
import io.github.sceneview.node.ModelNode
import io.github.sceneview.math.Position

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selectedAsset by remember { mutableStateOf<AssetInfo?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAsset = loadAsset(context, uri)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("2D / 3D Model Viewer") },
                actions = {
                    IconButton(
                        onClick = { showInfoDialog = true },
                        enabled = selectedAsset != null
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = "Informacje o pliku")
                    }
                    IconButton(
                        onClick = { openFileLauncher.launch(arrayOf("*/*")) }
                    ) {
                        Icon(Icons.Rounded.AddCircle, contentDescription = "Otwórz plik")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            RenderSurface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                asset = selectedAsset
            )
            StatusBar(asset = selectedAsset)
        }
    }
    if (showInfoDialog && selectedAsset != null) {
        AssetInfoDialog(
            asset = selectedAsset!!,
            onDismiss = { showInfoDialog = false }
        )
    }
}

@Composable
fun RenderSurface(
    modifier: Modifier,
    asset: AssetInfo?
) {
    Surface(modifier = modifier) {
        when (asset) {
            null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Nie załadowano pliku")
            }
            is ImageAsset -> {
                Render2D(asset)
            }
            is Model3DAsset -> {
                Render3D(asset)
            }
        }
    }
}
@Composable
fun Render2D(asset: ImageAsset) {
    val context = LocalContext.current
    val uri = Uri.parse(asset.base.uri!!)
    val imageBitmap by remember(uri) {
        mutableStateOf(
            if (asset.imageInfo.isVector)
                loadSvgBitmap(context, uri)
            else
                loadRasterBitmap(context, uri)
        )
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoom, pan, rotate ->
        scale = (scale * zoom).coerceIn(0.2f, 10f)
        rotation += rotate
        offset += pan
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .transformable(transformState)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        rotation = 0f
                        offset = Offset.Zero
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = asset.base.fileName,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
                translationX = offset.x
                translationY = offset.y
            }
        )
    }
}

@Composable
fun Render3D(
    asset: Model3DAsset,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sceneView = remember {
        SceneView(context)
    }
    DisposableEffect(asset.base.uri) {
        val uri = Uri.parse(asset.base.uri!!)
        val modelFile = copyUriToCacheFile(
            context = context,
            uri = uri,
            fileName = asset.base.fileName
        )
        var modelNode: ModelNode? = null

        runCatching {
            val modelInstance = sceneView.modelLoader
                .createModelInstance(modelFile)
            modelNode = ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 1.0f,
                centerOrigin = Position(0f, -1f, 0f)
            )
            sceneView.addChildNode(modelNode)
        }

        onDispose {
            runCatching {
                modelNode?.let { node ->
                    sceneView.removeChildNode(node)
                    node.destroy()
                }
            }
            runCatching {
                sceneView.destroy()
            }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { sceneView }
    )
}

@Composable
fun AssetInfoDialog(
    asset: AssetInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Informacje o pliku") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (asset) {
                    is ImageAsset -> ImageInfoContent(asset)
                    is Model3DAsset -> Model3DInfoContent(asset)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun BaseInfoContent(base: ModelInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Nazwa pliku: ${base.fileName}")
        Text("Ścieżka / URI: ${base.filePath}")
        Text("Format: ${base.format}")
        Text("Rozszerzenie: ${base.extension}")
        HorizontalDivider()
        Text("Rozmiar: ${base.fileSizeBytes} B")
        Text("Typ MIME: ${base.mimeType ?: "—"}")
        HorizontalDivider()
        Text("Data utworzenia: ${base.createdAt ?: "—"}")
        Text("Data modyfikacji: ${base.modifiedAt ?: "—"}")
        Text("Ostatni dostęp: ${base.accessedAt ?: "—"}")
        HorizontalDivider()
        Text("Źródło: ${if (base.isEmbedded) "assets aplikacji" else "plik zewnętrzny"}")
        Text(
            "Uprawnienia: " +
                    (if (base.isReadable) "R" else "-") +
                    (if (base.isWritable) "W" else "-")
        )
    }
}

@Composable
fun ImageInfoContent(asset: ImageAsset) {
    val base = asset.base
    val img = asset.imageInfo
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Informacje o pliku", style = MaterialTheme.typography.titleSmall)
        BaseInfoContent(base)
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        Text("Geometria obrazu", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Wektorowy: ${if (img.isVector) "tak" else "nie"}")
            Text("Szerokość: ${img.widthPx ?: "—"} px")
            Text("Wysokość: ${img.heightPx ?: "—"} px")
            Text("ViewBox szerokość: ${img.viewBoxWidth ?: "—"}")
            Text("ViewBox wysokość: ${img.viewBoxHeight ?: "—"}")
            Text("Proporcje: ${img.aspectRatio ?: "—"}")
        }
        HorizontalDivider()
        Text("Właściwości obrazu", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Kanał alfa: ${img.hasAlpha ?: "—"}")
            Text("Model koloru: ${img.colorModel ?: "—"}")
            Text("Głębia koloru: ${img.bitDepthPerChannel ?: "—"} bpc")
            Text("DPI X: ${img.dpiX ?: "—"}")
            Text("DPI Y: ${img.dpiY ?: "—"}")
        }
        HorizontalDivider()
        Text("EXIF – czas", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Data: ${img.exifDateTime ?: "—"}")
            Text("Data oryginalna: ${img.exifDateTimeOriginal ?: "—"}")
            Text("Data digitalizacji: ${img.exifDateTimeDigitized ?: "—"}")
            Text("Subsekundy: ${img.subSecTime ?: "—"}")
            Text("Orientacja: ${img.exifOrientation ?: "—"}")
        }
        HorizontalDivider()
        Text("EXIF – aparat / obiektyw", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Producent: ${img.cameraMake ?: "—"}")
            Text("Model: ${img.cameraModel ?: "—"}")
            Text("Oprogramowanie: ${img.software ?: "—"}")
            Text("Producent obiektywu: ${img.lensMake ?: "—"}")
            Text("Model obiektywu: ${img.lensModel ?: "—"}")
        }
        HorizontalDivider()
        Text("EXIF – ekspozycja", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Czas ekspozycji: ${img.exposureTime ?: "—"} s")
            Text("Przysłona (f): ${img.fNumber ?: "—"}")
            Text("ISO: ${img.isoSpeed ?: "—"}")
            Text("Ogniskowa: ${img.focalLength ?: "—"} mm")
            Text("Korekta ekspozycji: ${img.exposureBias ?: "—"} EV")
            Text("Tryb pomiaru: ${img.meteringMode ?: "—"}")
            Text("Balans bieli: ${img.whiteBalance ?: "—"}")
            Text("Lampa błyskowa: ${img.flash ?: "—"}")
        }
        HorizontalDivider()
        Text("EXIF – obraz", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Jasność: ${img.brightnessValue ?: "—"}")
            Text("Kontrast: ${img.contrast ?: "—"}")
            Text("Nasycenie: ${img.saturation ?: "—"}")
            Text("Ostrość: ${img.sharpness ?: "—"}")
        }
        HorizontalDivider()
        Text("EXIF – lokalizacja", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Zawiera GPS: ${if (img.hasGpsMetadata == true) "tak" else "nie"}")
            Text("Szerokość geograficzna: ${img.gpsLatitude ?: "—"}")
            Text("Długość geograficzna: ${img.gpsLongitude ?: "—"}")
            Text("Wysokość: ${img.gpsAltitude ?: "—"}")
            Text("Czas GPS: ${img.gpsTimestamp ?: "—"}")
        }
    }
}


@Composable
fun Model3DInfoContent(asset: Model3DAsset) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Informacje o pliku", style = MaterialTheme.typography.titleSmall)
        BaseInfoContent(asset.base)
    }
}

@Composable
fun StatusBar(asset: AssetInfo?) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = asset?.base?.fileName ?: "Brak załadowanego pliku",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}