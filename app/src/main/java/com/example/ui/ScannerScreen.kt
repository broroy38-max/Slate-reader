package com.example.ui

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.scanner.ScanPage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit,
    onOpenBook: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanQueue by viewModel.scanQueue.collectAsState()

    // Permissions State
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    // CameraX Properties
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Active refinery editing states
    var activeEditPage by remember { mutableStateOf<ScanPage?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    var pdfName by remember { mutableStateOf("New Scan File") }

    // Gallery Snapshot photo picker
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val file = copyGalleryUriToTemp(uri, context)
            if (file != null) {
                viewModel.addPhotoToScanQueue(file.absolutePath)
            }
        }
    }

    // Launch Camera Provider setup when camera permission is granted
    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted) {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan & Create PDF") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Exit Scanner")
                    }
                },
                actions = {
                    IconButton(onClick = { galleryPickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Import Photos")
                    }
                    if (scanQueue.isNotEmpty()) {
                        Button(
                            onClick = { showNameDialog = true },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("compile_pdf_button")
                        ) {
                            Text("Export (${scanQueue.size})")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF1E1E24)) // Dark workspace background
        ) {
            // CAMERA PREVIEW SCREEN SECTION
            if (activeEditPage == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (cameraPermissionState.status.isGranted) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Central overlay focusing brackets
                        Box(
                            modifier = Modifier
                                .size(240.dp, 320.dp)
                                .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .align(Alignment.Center)
                        )

                        // Bottom Action layout
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Circular shutter trigger button
                            Button(
                                onClick = {
                                    takeCameraPhoto(context, imageCapture) { photoFile ->
                                        viewModel.addPhotoToScanQueue(photoFile.absolutePath)
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(72.dp)
                                    .border(4.dp, Color.White, CircleShape)
                                    .testTag("shutter_trigger"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Camera, contentDescription = "Shutter Capture", tint = Color.White)
                            }
                        }
                    } else {
                        // Permissions Empty State views
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(72.dp), tint = Color.Gray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Camera Permission Required",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Allow camera permissions to capture document pages directly inside Slate Reader.",
                                color = Color.LightGray,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }
            } else {
                // PHOTO REFINERY WORKBENCH (Crop and color adjustment overlay dialog)
                PhotoRefineryWorkbench(
                    page = activeEditPage!!,
                    onSave = { updated ->
                        viewModel.adjustScanPageSettings(
                            updated.id,
                            updated.brightness,
                            updated.contrast,
                            updated.thresholdingEnabled
                        )
                        activeEditPage = null
                    },
                    onCancel = { activeEditPage = null }
                )
            }

            // BOTTOM QUEUE CAROUSEL THUMBNAILS
            if (scanQueue.isNotEmpty() && activeEditPage == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            text = "COMPILATION QUEUE (${scanQueue.size} Pages)",
                            color = Color.LightGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(scanQueue) { idx, page ->
                                ScanQueueCard(
                                    page = page,
                                    index = idx,
                                    onEdit = { activeEditPage = page },
                                    onRotate = { viewModel.rotateScanPage(page.id) },
                                    onDelete = { viewModel.removeScanPage(page.id) },
                                    onMoveLeft = { if (idx > 0) viewModel.swapScanPages(idx, idx - 1) },
                                    onMoveRight = { if (idx < scanQueue.size - 1) viewModel.swapScanPages(idx, idx + 1) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Name Confirmation Export Dialog
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Generate PDF Document") },
            text = {
                Column {
                    Text(
                        "Input a name identifier for this scanned PDF file. The file is saved directly into the Browser directory.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = pdfName,
                        onValueChange = { pdfName = it },
                        singleLine = true,
                        placeholder = { Text("Title Name...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_title_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pdfName.isNotBlank()) {
                            viewModel.compileScannerPdf(pdfName) { targetFile ->
                                onOpenBook(targetFile)
                            }
                            showNameDialog = false
                        }
                    }
                ) {
                    Text("Save & Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ScanQueueCard(
    page: ScanPage,
    index: Int,
    onEdit: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(110.dp)
            .fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C35))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = page.originalImagePath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Page badge counter
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .size(20.dp)
                        .align(Alignment.TopStart),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${index + 1}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Row action tools (delete)
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Page", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }

            // Quick tools toolbar footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF3E3E4D))
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMoveLeft, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Move Left", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onRotate, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Crop, contentDescription = "Crop adjustment", tint = Color.White, modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onMoveRight, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Move Right", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// -------------------------------------------------------------
// REFINERY WORKSPACE CONTAINER
// -------------------------------------------------------------
@Composable
fun PhotoRefineryWorkbench(
    page: ScanPage,
    onSave: (ScanPage) -> Unit,
    onCancel: () -> Unit
) {
    var bright by remember { mutableStateOf(page.brightness) }
    var contr by remember { mutableStateOf(page.contrast) }
    var threshEnabled by remember { mutableStateOf(page.thresholdingEnabled) }

    // Perspective Corners offset maps (Coordinates 0f to 1f)
    var tl by remember { mutableStateOf(Offset(page.topLeftX, page.topLeftY)) }
    var tr by remember { mutableStateOf(Offset(page.topRightX, page.topRightY)) }
    var br by remember { mutableStateOf(Offset(page.bottomRightX, page.bottomRightY)) }
    var bl by remember { mutableStateOf(Offset(page.bottomLeftX, page.bottomLeftY)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131317))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Refine Document Page Layout", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row {
                TextButton(onClick = onCancel) { Text("Cancel", color = Color.Gray) }
                Button(onClick = {
                    val updated = page.copy(
                        brightness = bright,
                        contrast = contr,
                        thresholdingEnabled = threshEnabled,
                        topLeftX = tl.x, topLeftY = tl.y,
                        topRightX = tr.x, topRightY = tr.y,
                        bottomRightX = br.x, bottomRightY = br.y,
                        bottomLeftX = bl.x, bottomLeftY = bl.y
                    )
                    onSave(updated)
                }) {
                    Text("Apply")
                }
            }
        }

        // Draggable Perspective editor Box
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(3f / 4f)
                .background(Color.Black)
                .border(1.dp, Color.DarkGray)
        ) {
            // Load Original
            AsyncImage(
                model = page.originalImagePath,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )

            // Geometry bounding coordinates calculation on Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(tl.x * size.width, tl.y * size.height)
                    lineTo(tr.x * size.width, tr.y * size.height)
                    lineTo(br.x * size.width, br.y * size.height)
                    lineTo(bl.x * size.width, bl.y * size.height)
                    close()
                }
                drawPath(
                    path = path,
                    color = Color.Green,
                    style = Stroke(width = 6f)
                )
            }

            // Draggable Anchor handles superimposed
            AnchorMarker(coordinate = tl, onDrag = { tl = checkBounds(tl + it) })
            AnchorMarker(coordinate = tr, onDrag = { tr = checkBounds(tr + it) })
            AnchorMarker(coordinate = br, onDrag = { br = checkBounds(br + it) })
            AnchorMarker(coordinate = bl, onDrag = { bl = checkBounds(bl + it) })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Image Enhancement Controllers
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222228))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("B&W Shadows Removal Threshold", color = Color.White, fontSize = 13.sp)
                    Switch(checked = threshEnabled, onCheckedChange = { threshEnabled = it })
                }

                // Brightness
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Brightness Boost", color = Color.LightGray, fontSize = 12.sp)
                        Text("${ (bright * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                    }
                    Slider(value = bright, onValueChange = { bright = it }, valueRange = 0.5f..1.8f)
                }

                // Contrast
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Contrast Ratio", color = Color.LightGray, fontSize = 12.sp)
                        Text("${ (contr * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                    }
                    Slider(value = contr, onValueChange = { contr = it }, valueRange = 0.5f..2.5f)
                }
            }
        }
    }
}

@Composable
fun BoxScope.AnchorMarker(
    coordinate: Offset,
    onDrag: (Offset) -> Unit
) {
    var widthPx by remember { mutableStateOf(0f) }
    var heightPx by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                widthPx = size.width.toFloat()
                heightPx = size.height.toFloat()
            }
    )

    // Compute pixel positioning inside viewport relative to 0f..1f maps
    val xPos = if (widthPx > 0) (coordinate.x * widthPx) - 18f else 0f
    val yPos = if (heightPx > 0) (coordinate.y * heightPx) - 18f else 0f

    Box(
        modifier = Modifier
            .offset { IntOffset(xPos.roundToInt(), yPos.roundToInt()) }
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Green.copy(alpha = 0.6f))
            .border(2.dp, Color.White, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Translate drag delta back into percentage coordinates
                    if (size.width > 0 && size.height > 0) {
                        val deltaPercent = Offset(
                            dragAmount.x / size.width.toFloat(),
                            dragAmount.y / size.height.toFloat()
                        )
                        onDrag(deltaPercent)
                    }
                }
            }
    )
}

private fun checkBounds(valIn: Offset): Offset {
    return Offset(
        valIn.x.coerceIn(0f, 1f),
        valIn.y.coerceIn(0f, 1f)
    )
}

// Helper sizes parser
private fun sizeMap(sizeIn: Int): androidx.compose.ui.unit.Dp {
    return sizeIn.dp
}

// -------------------------------------------------------------
// CAMERA CAPTURING & SYSTEM PRE-WRAPPING UTILS
// -------------------------------------------------------------
private fun takeCameraPhoto(
    context: Context,
    imageCapture: ImageCapture,
    onTaken: (File) -> Unit
) {
    val photoFile = File(
        context.cacheDir,
        "snap_${UUID.randomUUID()}.jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onTaken(photoFile)
            }

            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
            }
        }
    )
}

private fun copyGalleryUriToTemp(uri: Uri, context: Context): File? {
    return try {
        val tempFile = File(context.cacheDir, "gallery_${UUID.randomUUID()}.jpg")
        val ip = context.contentResolver.openInputStream(uri) ?: return null
        val op = FileOutputStream(tempFile)
        ip.copyTo(op)
        ip.close()
        op.close()
        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
