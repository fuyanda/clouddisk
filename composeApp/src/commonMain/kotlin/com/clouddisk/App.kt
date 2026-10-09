package com.clouddisk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import coil3.compose.AsyncImage
import coil3.compose.setSingletonImageLoaderFactory
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.clouddisk.platform.VideoPlayer
import com.clouddisk.platform.openExternal
import com.clouddisk.platform.rememberFileSaver
import com.clouddisk.platform.rememberUploadPicker
import com.clouddisk.storage.*
import kotlinx.coroutines.launch
import kotlin.random.Random

private data class Transfer(val label: String, val done: Long, val total: Long)

private sealed interface Screen {
    data object AccountList : Screen
    data class Editor(val account: Account?) : Screen
    data class Browser(val account: Account) : Screen
}

@Composable
fun App() {
    setSingletonImageLoaderFactory { ctx ->
        ImageLoader.Builder(ctx)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }

    MaterialTheme {
        var accounts by remember { mutableStateOf(loadAccounts()) }
        var screen by remember { mutableStateOf<Screen>(Screen.AccountList) }

        when (val s = screen) {
            is Screen.AccountList -> AccountListScreen(
                accounts = accounts,
                onAdd = { screen = Screen.Editor(null) },
                onEdit = { screen = Screen.Editor(it) },
                onOpen = { screen = Screen.Browser(it) },
                onDelete = { acc ->
                    accounts = accounts.filterNot { it.id == acc.id }
                    saveAccounts(accounts)
                },
            )

            is Screen.Editor -> AccountEditorScreen(
                existing = s.account,
                onCancel = { screen = Screen.AccountList },
                onSave = { acc ->
                    accounts = accounts.filterNot { it.id == acc.id } + acc
                    saveAccounts(accounts)
                    screen = Screen.AccountList
                },
            )

            is Screen.Browser -> BrowserScreen(
                account = s.account,
                onBack = { screen = Screen.AccountList },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountListScreen(
    accounts: List<Account>,
    onAdd: () -> Unit,
    onEdit: (Account) -> Unit,
    onOpen: (Account) -> Unit,
    onDelete: (Account) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("云盘 · 存储账号") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("添加") })
        },
    ) { padding ->
        if (accounts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有账号，点右下角添加")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(accounts, key = { it.id }) { acc ->
                    ListItem(
                        headlineContent = { Text(acc.name) },
                        supportingContent = { Text("${presetOf(acc.providerId).displayName} · ${acc.bucket}") },
                        leadingContent = { Icon(Icons.Default.Cloud, null) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onEdit(acc) }) { Icon(Icons.Default.Edit, "编辑") }
                                IconButton(onClick = { onDelete(acc) }) { Icon(Icons.Default.Delete, "删除") }
                            }
                        },
                        modifier = Modifier.clickable { onOpen(acc) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountEditorScreen(
    existing: Account?,
    onCancel: () -> Unit,
    onSave: (Account) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var providerId by remember { mutableStateOf(existing?.providerId ?: providerPresets.first().id) }
    var region by remember { mutableStateOf(existing?.region ?: presetOf(providerId).defaultRegion) }
    var endpoint by remember { mutableStateOf(existing?.endpoint ?: resolveEndpoint(presetOf(providerId), region)) }
    var bucket by remember { mutableStateOf(existing?.bucket ?: "") }
    var accessKey by remember { mutableStateOf(existing?.accessKey ?: "") }
    var secretKey by remember { mutableStateOf(existing?.secretKey ?: "") }
    var providerMenu by remember { mutableStateOf(false) }

    val preset = presetOf(providerId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "新建账号" else "编辑账号") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExposedDropdownMenuBox(expanded = providerMenu, onExpandedChange = { providerMenu = it }) {
                OutlinedTextField(
                    value = preset.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("厂商") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerMenu) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                    providerPresets.forEach { p ->
                        DropdownMenuItem(text = { Text(p.displayName) }, onClick = {
                            providerId = p.id
                            region = p.defaultRegion
                            endpoint = resolveEndpoint(p, p.defaultRegion)
                            providerMenu = false
                        })
                    }
                }
            }

            OutlinedTextField(name, { name = it }, label = { Text("账号名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                region,
                {
                    region = it
                    if (!preset.custom) endpoint = resolveEndpoint(preset, it)
                },
                label = { Text("区域 Region") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                endpoint,
                { endpoint = it },
                label = { Text("端点 Endpoint") },
                enabled = preset.custom,
                supportingText = { if (!preset.custom) Text("由厂商+区域自动生成，可切到自定义修改") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(bucket, { bucket = it }, label = { Text("Bucket") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(accessKey, { accessKey = it }, label = { Text("AccessKey") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                secretKey,
                { secretKey = it },
                label = { Text("SecretKey") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    onSave(
                        Account(
                            id = existing?.id ?: Random.nextLong().toString(),
                            name = name.ifBlank { "${preset.displayName}-$bucket" },
                            providerId = providerId,
                            endpoint = endpoint,
                            region = region,
                            bucket = bucket,
                            accessKey = accessKey,
                            secretKey = secretKey,
                            pathStyle = preset.pathStyle,
                        ),
                    )
                },
                enabled = bucket.isNotBlank() && accessKey.isNotBlank() && secretKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
        }
    }
}

private fun isImage(name: String) = name.substringAfterLast('.', "").lowercase() in
    setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

private fun isVideo(name: String) = name.substringAfterLast('.', "").lowercase() in
    setOf("mp4", "mov", "mkv", "avi", "webm", "m4v")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScreen(account: Account, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val storage = remember(account.id) { createStorage(account) }
    DisposableEffect(account.id) { onDispose { storage.close() } }

    var prefix by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<StorageItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var previewImages by remember { mutableStateOf<List<StorageItem>>(emptyList()) }
    var previewIndex by remember { mutableStateOf(-1) }
    var previewVideoUrl by remember { mutableStateOf<String?>(null) }
    var shareItem by remember { mutableStateOf<StorageItem?>(null) }
    var deleteItem by remember { mutableStateOf<StorageItem?>(null) }

    var transfer by remember { mutableStateOf<Transfer?>(null) }

    val uploadPicker = rememberUploadPicker { fileName, localPath ->
        scope.launch {
            transfer = Transfer("上传 $fileName", 0, -1)
            runCatching {
                storage.upload(prefix + fileName, localPath) { done, total ->
                    transfer = Transfer("上传 $fileName", done, total)
                }
            }
                .onSuccess { status = "已上传 $fileName"; items = storage.list(prefix) }
                .onFailure { error = it.message }
            transfer = null
        }
    }
    val saver = rememberFileSaver()
    var gridView by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true; error = null
        runCatching { storage.list(prefix) }
            .onSuccess { items = it }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    fun open(item: StorageItem) {
        when {
            item.isFolder -> prefix = item.key
            isImage(item.name) -> {
                previewImages = items.filter { isImage(it.name) }
                previewIndex = previewImages.indexOfFirst { it.key == item.key }
            }
            isVideo(item.name) -> scope.launch { previewVideoUrl = storage.presignUrl(item.key) }
            else -> scope.launch { openExternal(storage.presignUrl(item.key)) }
        }
    }

    fun startDownload(item: StorageItem) {
        scope.launch {
            transfer = Transfer("下载 ${item.name}", 0, -1)
            runCatching {
                storage.download(item.key) { done, total -> transfer = Transfer("下载 ${item.name}", done, total) }
            }
                .onSuccess { path -> saver.save(item.name, path) }
                .onFailure { error = it.message }
            transfer = null
        }
    }

    LaunchedEffect(prefix) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (prefix.isEmpty()) account.bucket else prefix, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (prefix.isEmpty()) onBack() else prefix = parentPrefix(prefix)
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { gridView = !gridView }) {
                        Icon(if (gridView) Icons.Default.ViewList else Icons.Default.GridView, "切换视图")
                    }
                    IconButton(onClick = { scope.launch { refresh() } }) { Icon(Icons.Default.Refresh, "刷新") }
                    IconButton(onClick = { showNewFolder = true }) { Icon(Icons.Default.CreateNewFolder, "新建文件夹") }
                    IconButton(onClick = uploadPicker) { Icon(Icons.Default.Upload, "上传") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            status?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary) }
            error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error) }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            transfer?.let { t ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    val suffix = if (t.total > 0) " / ${humanSize(t.total)}" else ""
                    Text("${t.label}  ${humanSize(t.done)}$suffix", style = MaterialTheme.typography.labelMedium)
                    if (t.total > 0) {
                        LinearProgressIndicator(
                            progress = { (t.done.toFloat() / t.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }

            if (gridView) {
                LazyVerticalGrid(columns = GridCells.Adaptive(112.dp), modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.key }) { item ->
                        GridCell(
                            item = item,
                            storage = storage,
                            onOpen = { open(item) },
                            onShare = { shareItem = item },
                            onDownload = { startDownload(item) },
                            onDelete = { deleteItem = item },
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.key }) { item ->
                        ListItem(
                            headlineContent = { Text(item.name) },
                            supportingContent = { if (!item.isFolder) Text(humanSize(item.size)) },
                            leadingContent = {
                                Icon(
                                    when {
                                        item.isFolder -> Icons.Default.Folder
                                        isImage(item.name) -> Icons.Default.Image
                                        isVideo(item.name) -> Icons.Default.Movie
                                        else -> Icons.AutoMirrored.Filled.InsertDriveFile
                                    },
                                    null,
                                )
                            },
                            trailingContent = {
                                Row {
                                    if (!item.isFolder) {
                                        IconButton(onClick = { shareItem = item }) { Icon(Icons.Default.Share, "分享") }
                                        IconButton(onClick = { startDownload(item) }) { Icon(Icons.Default.Download, "下载") }
                                    }
                                    IconButton(onClick = { deleteItem = item }) { Icon(Icons.Default.Delete, "删除") }
                                }
                            },
                            modifier = Modifier.clickable { open(item) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showNewFolder) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("新建文件夹") },
            text = { OutlinedTextField(folderName, { folderName = it }, label = { Text("名称") }) },
            confirmButton = {
                TextButton(onClick = {
                    val fn = folderName.trim()
                    showNewFolder = false
                    if (fn.isNotEmpty()) scope.launch {
                        runCatching { storage.createFolder(prefix + fn) }
                            .onSuccess { refresh() }
                            .onFailure { error = it.message }
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showNewFolder = false }) { Text("取消") } },
        )
    }

    if (previewIndex in previewImages.indices) {
        ImagePreviewDialog(
            storage = storage,
            images = previewImages,
            index = previewIndex,
            onIndexChange = { previewIndex = it },
            onDismiss = { previewIndex = -1 },
        )
    }

    previewVideoUrl?.let { url ->
        VideoPreviewDialog(url = url, onDismiss = { previewVideoUrl = null })
    }

    shareItem?.let { item ->
        ShareDialog(item = item, storage = storage, onDismiss = { shareItem = null })
    }

    deleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text("删除") },
            text = {
                Text(
                    if (item.isFolder) "确定删除文件夹「${item.name}」及其全部内容？此操作不可恢复。"
                    else "确定删除「${item.name}」？此操作不可恢复。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteItem = null
                    scope.launch {
                        runCatching { storage.delete(item.key, item.isFolder) }
                            .onSuccess { status = "已删除 ${item.name}"; refresh() }
                            .onFailure { error = it.message }
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ImagePreviewDialog(
    storage: StorageProvider,
    images: List<StorageItem>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val item = images[index]
    var url by remember(item.key) { mutableStateOf<String?>(null) }
    // 变换状态随图片 key 重置，切到上/下一张时自动回到初始视角
    var rotation by remember(item.key) { mutableStateOf(0f) }
    var scale by remember(item.key) { mutableStateOf(1f) }
    var offset by remember(item.key) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(item.key) { url = runCatching { storage.presignUrl(item.key) }.getOrNull() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))) {
            if (url == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            } else {
                AsyncImage(
                    model = url,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(item.key) {
                            detectTransformGestures { _, pan, zoom, rot ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                rotation += rot
                                offset += pan
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            rotationZ = rotation
                            translationX = offset.x; translationY = offset.y
                        },
                )
            }

            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Close, "关闭", tint = Color.White)
            }
            Text(
                "${index + 1}/${images.size}",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            )
            IconButton(
                onClick = { onIndexChange(index - 1) },
                enabled = index > 0,
                modifier = Modifier.align(Alignment.CenterStart),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "上一张", tint = Color.White) }
            IconButton(
                onClick = { onIndexChange(index + 1) },
                enabled = index < images.lastIndex,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "下一张", tint = Color.White) }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { rotation -= 90f }) { Icon(Icons.Default.RotateLeft, "左转", tint = Color.White) }
                IconButton(onClick = { rotation += 90f }) { Icon(Icons.Default.RotateRight, "右转", tint = Color.White) }
                IconButton(onClick = { scale = (scale - 0.5f).coerceAtLeast(1f) }) { Icon(Icons.Default.ZoomOut, "缩小", tint = Color.White) }
                IconButton(onClick = { scale = (scale + 0.5f).coerceAtMost(6f) }) { Icon(Icons.Default.ZoomIn, "放大", tint = Color.White) }
                IconButton(onClick = { scale = 1f; rotation = 0f; offset = Offset.Zero }) { Icon(Icons.Default.Refresh, "复位", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun VideoPreviewDialog(url: String, onDismiss: () -> Unit) {
    val rates = listOf(0.5f, 1f, 1.5f, 2f)
    var rate by remember { mutableStateOf(1f) }
    var scale by remember { mutableStateOf(1f) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoPlayer(
                url = url,
                rate = rate,
                modifier = Modifier.align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 4f) }
                    }
                    .graphicsLayer { scaleX = scale; scaleY = scale },
            )

            Row(
                modifier = Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                rates.forEach { r ->
                    FilterChip(
                        selected = rate == r,
                        onClick = { rate = r },
                        label = { Text("${r}x") },
                    )
                }
                IconButton(onClick = { scale = (scale - 0.5f).coerceAtLeast(1f) }) { Icon(Icons.Default.ZoomOut, "缩小", tint = Color.White) }
                IconButton(onClick = { scale = (scale + 0.5f).coerceAtMost(4f) }) { Icon(Icons.Default.ZoomIn, "放大", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun GridCell(
    item: StorageItem,
    storage: StorageProvider,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var thumbUrl by remember(item.key) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.key) {
        if (isImage(item.name)) thumbUrl = runCatching { storage.presignUrl(item.key) }.getOrNull()
    }
    Column(
        Modifier.padding(6.dp).clickable { onOpen() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            when {
                item.isFolder -> Icon(Icons.Default.Folder, null, Modifier.size(56.dp))
                isImage(item.name) && thumbUrl != null -> AsyncImage(
                    model = thumbUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                isVideo(item.name) -> Icon(Icons.Default.Movie, null, Modifier.size(56.dp))
                else -> Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, Modifier.size(56.dp))
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!item.isFolder) {
                        DropdownMenuItem(text = { Text("下载") }, onClick = { menu = false; onDownload() })
                        DropdownMenuItem(text = { Text("分享") }, onClick = { menu = false; onShare() })
                    }
                    DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Text(
            item.name,
            maxLines = 2,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val shareExpiries: List<Pair<String, Duration>> = listOf(
    "1 小时" to 1.hours,
    "6 小时" to 6.hours,
    "24 小时" to 24.hours,
    "7 天" to 7.days,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareDialog(item: StorageItem, storage: StorageProvider, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var expiry by remember { mutableStateOf(shareExpiries[2]) } // 默认 24 小时
    var url by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享 ${item.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }) {
                    OutlinedTextField(
                        value = expiry.first,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("有效期") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menu) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        shareExpiries.forEach { opt ->
                            DropdownMenuItem(text = { Text(opt.first) }, onClick = {
                                expiry = opt; url = null; menu = false
                            })
                        }
                    }
                }
                url?.let {
                    OutlinedTextField(
                        value = it,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分享链接") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (url == null) {
                TextButton(enabled = !working, onClick = {
                    scope.launch {
                        working = true
                        url = runCatching { storage.presignUrl(item.key, expiry.second) }.getOrElse { "生成失败：${it.message}" }
                        working = false
                    }
                }) { Text("生成链接") }
            } else {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(url!!))
                    onDismiss()
                }) { Text("复制并关闭") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${(kb * 10).toInt() / 10.0} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${(mb * 10).toInt() / 10.0} MB"
    return "${(mb / 1024.0 * 10).toInt() / 10.0} GB"
}
