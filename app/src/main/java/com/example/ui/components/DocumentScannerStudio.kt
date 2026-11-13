package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.domain.document.*
import com.example.domain.util.QatarFormatters
import com.example.ui.OneHomeProViewModel
import com.example.ui.OneHomeUiState
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerStudioDialog(
    uiState: OneHomeUiState,
    vm: OneHomeProViewModel,
    initialTargetType: DocumentTargetType? = null,
    initialTargetId: String? = null,
    initialCategory: DocumentCategoryOption = DocumentCategoryOption.QID,
    onDismiss: () -> Unit
) {
    val ctx = uiState.authContext ?: return
    val data = uiState.data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isCustomerPortal = ctx.isCustomerPortalOnly()

    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var selectedTargetType by remember {
        mutableStateOf(
            if (isCustomerPortal) DocumentTargetType.TENANT
            else initialTargetType ?: when {
                data.customers.isNotEmpty() -> DocumentTargetType.TENANT
                data.properties.isNotEmpty() -> DocumentTargetType.PROPERTY
                else -> DocumentTargetType.BUSINESS
            }
        )
    }
    var selectedTargetId by remember {
        mutableStateOf(
            if (isCustomerPortal) (ctx.customerId ?: "")
            else initialTargetId.orEmpty()
        )
    }
    var title by remember { mutableStateOf("") }
    var isRestricted by remember { mutableStateOf(true) }
    var outputFormat by remember { mutableStateOf(DocumentOutputFormat.PDF) }

    // Staged pages (for Photo / Scan / Gallery images) OR direct picked file URI (PDF / File)
    var stagedPages by remember { mutableStateOf<List<ScannedPageDraft>>(emptyList()) }
    var activePageIndex by remember { mutableIntStateOf(0) }
    var directPickedFileUri by remember { mutableStateOf<Uri?>(null) }
    var directPickedFileInfo by remember { mutableStateOf<ValidatedUploadFile?>(null) }

    var pendingCameraIsScan by remember { mutableStateOf(true) }
    var replacePageIndex by remember { mutableStateOf<Int?>(null) }
    var showCameraViewfinder by remember { mutableStateOf(false) }
    var showCameraPermissionRationale by remember { mutableStateOf(false) }
    var showConfirmUploadDialog by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var isProcessingImage by remember { mutableStateOf(false) }

    // Target records list for chosen target type
    val targetChoices: List<Pair<String, String>> = remember(selectedTargetType, data, ctx) {
        if (isCustomerPortal) {
            listOf((ctx.customerId ?: "") to "${ctx.fullName} (My Tenant Profile)")
        } else {
            when (selectedTargetType) {
                DocumentTargetType.TENANT -> data.customers.map { it.id to "${it.fullName} • QID ${it.qidNumber.ifBlank { "-" }}" }
                DocumentTargetType.PROPERTY -> data.properties.map { it.id to "${it.code} • ${it.name}" }
                DocumentTargetType.EMPLOYEE -> data.employees.map { it.id to "${it.fullName} • ${it.jobTitle}" }
                DocumentTargetType.AGREEMENT -> data.agreements.map { ag ->
                    val cust = data.customers.firstOrNull { it.id == ag.customerId }?.fullName ?: "Tenant"
                    ag.id to "${ag.agreementNumber} • $cust"
                }
                DocumentTargetType.PAYMENT -> data.payments.map { pay ->
                    pay.id to "${pay.method} • ${QatarFormatters.formatQar(pay.amountFils)} • ${pay.referenceNumber.ifBlank { pay.id.take(6) }}"
                }
                DocumentTargetType.BUSINESS -> listOf(ctx.activeBusinessId to "Company / Active Branch")
            }
        }
    }

    LaunchedEffect(targetChoices, selectedTargetType) {
        if (targetChoices.none { it.first == selectedTargetId }) {
            selectedTargetId = targetChoices.firstOrNull()?.first.orEmpty()
        }
    }

    fun ingestImageUris(uris: List<Uri>, autoCropEdges: Boolean, replaceIdx: Int?) {
        if (uris.isEmpty()) return
        scope.launch {
            isProcessingImage = true
            localError = null
            directPickedFileUri = null
            directPickedFileInfo = null
            try {
                val created = withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        val bmp = DocumentImageProcessor.loadOrientedBitmap(context, uri)
                        val detected = DocumentImageProcessor.detectDocumentBounds(bmp)
                        val initialCrop = if (autoCropEdges) detected else NormalizedCropRect()
                        val mode = if (autoCropEdges) ReadabilityMode.AUTO_ENHANCE else ReadabilityMode.ORIGINAL
                        val processed = DocumentImageProcessor.processPageBitmap(bmp, 0, initialCrop, mode)
                        ScannedPageDraft(
                            id = UUID.randomUUID().toString(),
                            sourceUri = uri,
                            sourceBitmap = bmp,
                            rotationDegrees = 0,
                            cropRect = initialCrop,
                            autoDetectedCrop = detected,
                            readabilityMode = mode,
                            processedBitmap = processed
                        )
                    }
                }
                if (replaceIdx != null && replaceIdx in stagedPages.indices && created.size == 1) {
                    stagedPages = stagedPages.toMutableList().also { it[replaceIdx] = created.first() }
                    activePageIndex = replaceIdx
                } else {
                    stagedPages = stagedPages + created
                    activePageIndex = (stagedPages.size - created.size).coerceAtLeast(0)
                }
                if (stagedPages.size > 1) {
                    outputFormat = DocumentOutputFormat.PDF
                }
            } catch (e: Exception) {
                localError = e.message ?: "Failed to load image"
            } finally {
                replacePageIndex = null
                isProcessingImage = false
            }
        }
    }

    fun updateActivePage(transform: (ScannedPageDraft) -> ScannedPageDraft) {
        val idx = activePageIndex
        if (idx !in stagedPages.indices) return
        val updatedDraft = transform(stagedPages[idx])
        scope.launch {
            val reprocessed = withContext(Dispatchers.IO) {
                DocumentImageProcessor.processPageBitmap(
                    updatedDraft.sourceBitmap,
                    updatedDraft.rotationDegrees,
                    updatedDraft.cropRect,
                    updatedDraft.readabilityMode
                )
            }
            stagedPages = stagedPages.toMutableList().also {
                it[idx] = updatedDraft.copy(processedBitmap = reprocessed)
            }
        }
    }

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCameraViewfinder = true
        } else {
            showCameraPermissionRationale = true
        }
    }

    fun launchCamera(isScanMode: Boolean, replacingIndex: Int? = null) {
        pendingCameraIsScan = isScanMode
        replacePageIndex = replacingIndex
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            showCameraViewfinder = true
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val pickGalleryMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        ingestImageUris(uris, autoCropEdges = false, replaceIdx = replacePageIndex)
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            localError = null
            try {
                val info = withContext(Dispatchers.IO) {
                    DocumentImageProcessor.validatePickedFile(context, uri)
                }
                if (info.mimeType == "application/pdf") {
                    stagedPages = emptyList()
                    directPickedFileUri = uri
                    directPickedFileInfo = info
                    outputFormat = DocumentOutputFormat.PDF
                    if (title.isBlank()) title = info.displayName.substringBeforeLast('.')
                } else {
                    ingestImageUris(listOf(uri), autoCropEdges = false, replaceIdx = null)
                }
            } catch (e: Exception) {
                localError = e.message ?: "Unsupported or unreadable file"
            }
        }
    }

    val uploadProgress = uiState.documentUploadProgress

    Dialog(
        onDismissRequest = {
            if (!uploadProgress.isUploading) {
                vm.clearDocumentUploadProgress()
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .widthIn(max = 880.dp)
                .testTag("document_scanner_studio"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(QatarNavy)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = ArchitecturalGold)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Camera & Document Scanner", color = PureWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Auto-edge crop, readability filter, multi-page PDF & RBAC vault", color = ArchitecturalGoldContainer, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(
                        enabled = !uploadProgress.isUploading,
                        onClick = {
                            vm.clearDocumentUploadProgress()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("scanner_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close scanner", tint = PureWhite)
                    }
                }

                // Upload Progress / Error / Success Banner
                if (uploadProgress.isUploading || isProcessingImage) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("upload_progress_box")) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                if (isProcessingImage) "Detecting edges & optimizing page..." else uploadProgress.stageLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (uploadProgress.isUploading) {
                                Text("${(uploadProgress.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        if (uploadProgress.isUploading) {
                            LinearProgressIndicator(progress = { uploadProgress.fraction }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                (localError ?: uploadProgress.errorMessage)?.let { err ->
                    Surface(color = CrimsonContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(10.dp)) {
                        Text(err, color = CrimsonError, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    // 4 Acquisition Options
                    item {
                        Text("1. Capture or Select Source", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { launchCamera(isScanMode = false) },
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("btn_take_photo")
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Take Photo", maxLines = 1)
                            }
                            Button(
                                onClick = { launchCamera(isScanMode = true) },
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("btn_scan_document")
                            ) {
                                Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Scan Doc", maxLines = 1)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    replacePageIndex = null
                                    pickGalleryMultiple.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("btn_choose_gallery")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Gallery", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { pickFileLauncher.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) },
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("btn_upload_file")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Upload File", maxLines = 1)
                            }
                        }
                    }

                    // Category & Target Record
                    item {
                        Text("2. Document Type & Record Linkage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DocumentCategoryOption.entries.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat.label) },
                                    modifier = Modifier.testTag("cat_chip_${cat.name}")
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (!isCustomerPortal) {
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DocumentTargetType.entries.forEach { target ->
                                    FilterChip(
                                        selected = selectedTargetType == target,
                                        onClick = { selectedTargetType = target },
                                        label = { Text(target.label) },
                                        modifier = Modifier.testTag("target_type_${target.name}")
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (targetChoices.isEmpty()) {
                            Text(
                                "No ${selectedTargetType.label} records exist yet. Create one first or select Company / Branch.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberWarning
                            )
                        } else {
                            val selectedPair = targetChoices.firstOrNull { it.first == selectedTargetId } ?: targetChoices.first()
                            DropdownSelector(
                                label = "Save Under ${selectedTargetType.label}*",
                                items = targetChoices,
                                selectedItem = selectedPair,
                                itemLabel = { it.second },
                                onSelect = { selectedTargetId = it.first },
                                testTag = "target_record_dropdown"
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Document Title (e.g. QID Front & Back)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("scanner_title_input")
                        )
                    }

                    // Preview & Multi-Page Editor
                    if (directPickedFileInfo != null) {
                        val fileInfo = directPickedFileInfo!!
                        item {
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(36.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(fileInfo.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("${fileInfo.mimeType} • ${(fileInfo.bytes.size / 1024).coerceAtLeast(1)} KB", style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = {
                                        directPickedFileUri = null
                                        directPickedFileInfo = null
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove file")
                                    }
                                }
                            }
                        }
                    } else if (stagedPages.isNotEmpty()) {
                        val currentPage = stagedPages.getOrNull(activePageIndex) ?: stagedPages.first()
                        item {
                            Text("3. Preview, Edit & Multi-Page Assembly (${stagedPages.size} page${if (stagedPages.size > 1) "s" else ""})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(230.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = currentPage.processedBitmap.asImageBitmap(),
                                            contentDescription = "Preview of scanned page ${activePageIndex + 1}",
                                            modifier = Modifier.fillMaxSize().padding(6.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                        Surface(
                                            color = QatarNavy.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                        ) {
                                            Text(
                                                "Page ${activePageIndex + 1} / ${stagedPages.size} • ${currentPage.readabilityMode.label}",
                                                color = PureWhite,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    // Page action bar (Retake, Rotate, Auto-Crop, Delete)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedButton(onClick = {
                                            updateActivePage { it.copy(cropRect = it.autoDetectedCrop) }
                                        }, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("btn_auto_crop")) {
                                            Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Auto Crop")
                                        }
                                        OutlinedButton(onClick = {
                                            updateActivePage { it.copy(rotationDegrees = (it.rotationDegrees + 270) % 360) }
                                        }, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("btn_rotate_left")) {
                                            Icon(Icons.Default.RotateLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("-90°")
                                        }
                                        OutlinedButton(onClick = {
                                            updateActivePage { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
                                        }, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("btn_rotate_right")) {
                                            Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("+90°")
                                        }
                                        OutlinedButton(onClick = {
                                            launchCamera(isScanMode = true, replacingIndex = activePageIndex)
                                        }, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("btn_retake_page")) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Retake")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val removeIdx = activePageIndex
                                                stagedPages = stagedPages.filterIndexed { i, _ -> i != removeIdx }
                                                activePageIndex = (removeIdx - 1).coerceAtLeast(0)
                                            },
                                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("btn_delete_page")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Delete", color = CrimsonError)
                                        }
                                    }

                                    // Readability Filter Chips
                                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ReadabilityMode.entries.forEach { mode ->
                                            FilterChip(
                                                selected = currentPage.readabilityMode == mode,
                                                onClick = { updateActivePage { it.copy(readabilityMode = mode) } },
                                                label = { Text(mode.label) }
                                            )
                                        }
                                    }

                                    // Fine Margin Crop Sliders
                                    Text("Fine Crop Margins", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Left ${(currentPage.cropRect.left * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                                            Slider(
                                                value = currentPage.cropRect.left,
                                                onValueChange = { v -> updateActivePage { it.copy(cropRect = it.cropRect.copy(left = v).normalized()) } },
                                                valueRange = 0f..0.4f
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Right ${(currentPage.cropRect.right * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                                            Slider(
                                                value = currentPage.cropRect.right,
                                                onValueChange = { v -> updateActivePage { it.copy(cropRect = it.cropRect.copy(right = v).normalized()) } },
                                                valueRange = 0.6f..1f
                                            )
                                        }
                                    }

                                    // Multi-page thumbnail strip + Add Page + Reorder
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        itemsIndexed(stagedPages, key = { _, p -> p.id }) { idx, page ->
                                            val isSelected = idx == activePageIndex
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(64.dp, 82.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .border(
                                                            width = if (isSelected) 2.5.dp else 1.dp,
                                                            color = if (isSelected) ArchitecturalGold else MaterialTheme.colorScheme.outline,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable { activePageIndex = idx }
                                                ) {
                                                    Image(
                                                        bitmap = page.processedBitmap.asImageBitmap(),
                                                        contentDescription = "Page ${idx + 1}",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                }
                                                Row {
                                                    if (idx > 0) {
                                                        IconButton(onClick = {
                                                            val list = stagedPages.toMutableList()
                                                            val item = list.removeAt(idx)
                                                            list.add(idx - 1, item)
                                                            stagedPages = list
                                                            activePageIndex = idx - 1
                                                        }, modifier = Modifier.size(28.dp)) {
                                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move page earlier", modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                    if (idx < stagedPages.lastIndex) {
                                                        IconButton(onClick = {
                                                            val list = stagedPages.toMutableList()
                                                            val item = list.removeAt(idx)
                                                            list.add(idx + 1, item)
                                                            stagedPages = list
                                                            activePageIndex = idx + 1
                                                        }, modifier = Modifier.size(28.dp)) {
                                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move page later", modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Output Format & Confidentiality
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Format:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        DocumentOutputFormat.entries.forEach { fmt ->
                                            val enabled = stagedPages.size == 1 || fmt == DocumentOutputFormat.PDF
                                            FilterChip(
                                                selected = (if (stagedPages.size > 1) DocumentOutputFormat.PDF else outputFormat) == fmt,
                                                enabled = enabled,
                                                onClick = { outputFormat = fmt },
                                                label = { Text(fmt.label) }
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = isRestricted, onCheckedChange = { isRestricted = it })
                                        Text("Restrict access & encrypt in private vault", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            EmptyStateCard(
                                title = "No Document Captured Yet",
                                description = "Tap Take Photo, Scan Doc, Gallery, or Upload File above. Multi-page scans automatically combine into a single PDF.",
                                icon = Icons.Default.DocumentScanner
                            )
                        }
                    }
                }

                // Bottom Confirmation Gate Bar (Never uploads without explicit user confirmation)
                val readyToConfirm = selectedTargetId.isNotBlank() &&
                    (directPickedFileUri != null || stagedPages.isNotEmpty()) &&
                    !uploadProgress.isUploading &&
                    !isProcessingImage
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = !uploadProgress.isUploading,
                        onClick = {
                            vm.clearDocumentUploadProgress()
                            onDismiss()
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        enabled = readyToConfirm,
                        onClick = { showConfirmUploadDialog = true },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("btn_review_and_confirm_upload")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Review & Upload")
                    }
                }
            }
        }
    }

    if (showCameraViewfinder) {
        CameraXDocumentViewfinderDialog(
            category = selectedCategory,
            isScanMode = pendingCameraIsScan,
            onCapturedUri = { uri ->
                showCameraViewfinder = false
                ingestImageUris(listOf(uri), autoCropEdges = pendingCameraIsScan, replaceIdx = replacePageIndex)
            },
            onError = { err ->
                showCameraViewfinder = false
                localError = err
            },
            onDismiss = { showCameraViewfinder = false }
        )
    }

    if (showCameraPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionRationale = false },
            title = { Text("Camera Permission Required") },
            text = {
                Text("Camera access is requested only when capturing photos or scanning documents. You can grant Camera permission or continue using Choose from Gallery / Upload File with zero permissions.")
            },
            confirmButton = {
                Button(onClick = {
                    showCameraPermissionRationale = false
                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }) { Text("Grant Camera") }
            },
            dismissButton = {
                TextButton(onClick = { showCameraPermissionRationale = false }) { Text("Close") }
            }
        )
    }

    // Mandatory User Confirmation Dialog before uploading any file
    if (showConfirmUploadDialog) {
        val targetSummary = targetChoices.firstOrNull { it.first == selectedTargetId }?.second ?: selectedTargetId
        val finalFormat = when {
            directPickedFileInfo != null -> directPickedFileInfo!!.extension.uppercase()
            stagedPages.size > 1 -> "PDF (${stagedPages.size} pages)"
            else -> outputFormat.label
        }
        val finalTitle = title.ifBlank { "${selectedCategory.label} Document" }

        AlertDialog(
            onDismissRequest = { showConfirmUploadDialog = false },
            title = { Text("Confirm Document Upload") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Please confirm before encrypting and saving to the vault:")
                    Text("• Title: $finalTitle", fontWeight = FontWeight.SemiBold)
                    Text("• Document Type: ${selectedCategory.label}")
                    Text("• Linked Record: ${selectedTargetType.label} — $targetSummary")
                    Text("• Output Format: $finalFormat")
                    Text("• Access Control: ${if (isRestricted) "Restricted RBAC Vault" else "Standard Business Vault"}")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmUploadDialog = false
                        val picked = directPickedFileUri
                        if (picked != null) {
                            vm.uploadVaultDocument(
                                uri = picked,
                                entityType = selectedTargetType.entityTypeCode,
                                entityId = selectedTargetId,
                                category = selectedCategory.code,
                                title = finalTitle,
                                isRestricted = isRestricted
                            ) { ok ->
                                if (ok) onDismiss()
                            }
                        } else if (stagedPages.isNotEmpty()) {
                            vm.uploadScannedPages(
                                pages = stagedPages,
                                outputFormat = outputFormat,
                                entityType = selectedTargetType.entityTypeCode,
                                entityId = selectedTargetId,
                                category = selectedCategory.code,
                                title = finalTitle,
                                isRestricted = isRestricted
                            ) { ok ->
                                if (ok) onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_final_upload")
                ) { Text("Confirm & Upload") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmUploadDialog = false }) { Text("Back to Edit") }
            }
        )
    }
}
