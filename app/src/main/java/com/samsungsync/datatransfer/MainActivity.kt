@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.samsungsync.datatransfer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File
import java.security.MessageDigest

private const val HOLD_DURATION_MS = 7000L
private const val PREFS_NAME = "vault_prefs"
private const val KEY_PIN_HASH = "pin_hash"
private const val KEY_VAULT_TITLE = "vault_title"
private const val KEY_SELECTED_APPS = "selected_apps"
private const val KEY_DISGUISE = "disguise_alias"
private const val DEFAULT_VAULT_TITLE = "Gizli Alan"

private sealed class Screen {
    data object FakeHome : Screen()
    data object PinSetup : Screen()
    data object PinEntry : Screen()
    data object SecretVault : Screen()
}

private object PinStore {
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hasPin(context: Context): Boolean =
        prefs(context).contains(KEY_PIN_HASH)

    fun savePin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun checkPin(context: Context, pin: String): Boolean =
        prefs(context).getString(KEY_PIN_HASH, null) == hash(pin)
}

private object VaultStore {
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTitle(context: Context): String =
        prefs(context).getString(KEY_VAULT_TITLE, DEFAULT_VAULT_TITLE) ?: DEFAULT_VAULT_TITLE

    fun setTitle(context: Context, title: String) {
        prefs(context).edit().putString(KEY_VAULT_TITLE, title).apply()
    }

    fun getSelectedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()

    fun setSelectedApps(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_SELECTED_APPS, packages).apply()
    }
}

private data class Disguise(val aliasSuffix: String, val displayLabel: String)

private val DISGUISES = listOf(
    Disguise("AliasDefault", "Varsayılan (Sistem Senkronizasyonu)"),
    Disguise("AliasNotes", "Notlar"),
    Disguise("AliasCalculator", "Hesap Makinesi"),
    Disguise("AliasGallery", "Galeri")
)

private object IconDisguise {
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun current(context: Context): String =
        prefs(context).getString(KEY_DISGUISE, DISGUISES.first().aliasSuffix)
            ?: DISGUISES.first().aliasSuffix

    fun apply(context: Context, aliasSuffix: String) {
        val pm = context.packageManager
        DISGUISES.forEach { disguise ->
            val component = ComponentName(context.packageName, "${context.packageName}.${disguise.aliasSuffix}")
            val state = if (disguise.aliasSuffix == aliasSuffix) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
        prefs(context).edit().putString(KEY_DISGUISE, aliasSuffix).apply()
    }
}

private object PhotoVault {
    private fun photosDir(context: Context): File =
        File(context.filesDir, "vault_photos").apply { mkdirs() }

    fun listPhotos(context: Context): List<File> =
        photosDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun newPhotoFile(context: Context): File =
        File(photosDir(context), "IMG_${System.currentTimeMillis()}.jpg")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.FakeHome) }

    when (screen) {
        is Screen.FakeHome -> FakeHomeScreen(
            onUnlockTriggered = {
                screen = if (PinStore.hasPin(context)) Screen.PinEntry else Screen.PinSetup
            }
        )
        is Screen.PinSetup -> PinSetupScreen(
            onPinCreated = { pin ->
                PinStore.savePin(context, pin)
                screen = Screen.SecretVault
            },
            onCancel = { screen = Screen.FakeHome }
        )
        is Screen.PinEntry -> PinEntryScreen(
            onCorrectPin = { screen = Screen.SecretVault },
            onCancel = { screen = Screen.FakeHome }
        )
        is Screen.SecretVault -> SecretVaultScreen(
            onLock = { screen = Screen.FakeHome }
        )
    }
}

@Composable
private fun FakeHomeScreen(onUnlockTriggered: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(HOLD_DURATION_MS)
            onUnlockTriggered()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Sistem Veri Aktarımı ve Eşitleme",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF424242)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Cihazlar arasında veri aktarımı hazırlanıyor…",
                fontSize = 13.sp,
                color = Color(0xFF9E9E9E)
            )

            Spacer(modifier = Modifier.weight(1f))

            TransferGraphic()

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "\u2699", fontSize = 32.sp, color = Color(0xFF757575))
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.weight(1f),
                    enabled = false
                ) { Text("Aktarımı Başlat") }

                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.weight(1f),
                    enabled = false
                ) { Text("Ayarlar") }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Hata Kodu: 0x800F081F - Sunucu bağlantısı zaman aşımına uğradı",
                fontSize = 9.sp,
                color = Color(0xFFBDBDBD)
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TransferGraphic() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFBDBDBD))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = "\u2601", fontSize = 28.sp, color = Color(0xFF9E9E9E))
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFBDBDBD))
        )
    }
}

@Composable
private fun PinEntryScreen(
    onCorrectPin: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Güvenlik PIN Kodu",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                        pin = it
                        error = false
                    }
                    if (pin.length == 4) {
                        if (PinStore.checkPin(context, pin)) {
                            onCorrectPin()
                        } else {
                            error = true
                            pin = ""
                        }
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword
                ),
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray
                ),
                modifier = Modifier.width(160.dp)
            )

            if (error) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Hatalı PIN", color = Color(0xFFE57373), fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onCancel) {
                Text(text = "İptal", color = Color.Gray)
            }
        }
    }
}

@Composable
private fun PinSetupScreen(
    onPinCreated: (String) -> Unit,
    onCancel: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = if (step == 0) "Yeni PIN Oluştur" else "PIN'i Tekrar Gir",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "4 haneli bir güvenlik PIN'i belirle",
                color = Color.Gray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = current,
                onValueChange = { value ->
                    if (value.length <= 4 && value.all { c -> c.isDigit() }) {
                        current = value
                        error = false
                    }
                    if (current.length == 4) {
                        if (step == 0) {
                            firstPin = current
                            current = ""
                            step = 1
                        } else {
                            if (current == firstPin) {
                                onPinCreated(current)
                            } else {
                                error = true
                                current = ""
                                firstPin = ""
                                step = 0
                            }
                        }
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword
                ),
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray
                ),
                modifier = Modifier.width(160.dp)
            )

            if (error) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "PIN'ler eşleşmedi, tekrar dene",
                    color = Color(0xFFE57373),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onCancel) {
                Text(text = "İptal", color = Color.Gray)
            }
        }
    }
}

private enum class VaultTab(val label: String) {
    APPS("Uygulamalar"), GALLERY("Galeri"), SETTINGS("Ayarlar")
}

@Composable
private fun SecretVaultScreen(onLock: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(VaultTab.APPS) }
    var vaultTitle by remember { mutableStateOf(VaultStore.getTitle(context)) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(vaultTitle) },
            actions = {
                TextButton(onClick = onLock) { Text("Kilitle") }
            }
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            VaultTab.values().forEach { t ->
                TextButton(
                    onClick = { tab = t },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = t.label,
                        fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Normal,
                        color = if (tab == t) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        }
        HorizontalDivider()

        when (tab) {
            VaultTab.APPS -> AppsTab()
            VaultTab.GALLERY -> GalleryTab()
            VaultTab.SETTINGS -> SettingsTab(
                vaultTitle = vaultTitle,
                onVaultTitleChange = {
                    vaultTitle = it
                    VaultStore.setTitle(context, it)
                }
            )
        }
    }
}

@Composable
private fun AppsTab() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(VaultStore.getSelectedApps(context)) }
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        AppPickerScreen(
            initiallySelected = selected,
            onDone = { newSelection ->
                selected = newSelection
                VaultStore.setSelectedApps(context, newSelection)
                showPicker = false
            },
            onCancel = { showPicker = false }
        )
        return
    }

    val pm = context.packageManager
    val visibleApps = remember(selected) {
        selected.mapNotNull { pkg ->
            try {
                val label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                pkg to label
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }.sortedBy { it.second.lowercase() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
                Text("+ Uygulama Ekle")
            }
            OutlinedButton(
                onClick = { openPlayStore(context) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Play Store'dan İndir")
            }
        }

        if (visibleApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Henüz uygulama eklenmedi.\n\"+ Uygulama Ekle\" ile başla.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visibleApps) { (packageName, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        supportingContent = { Text(packageName, fontSize = 11.sp) },
                        modifier = Modifier.clickableLaunch(context, packageName)
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun openPlayStore(context: Context) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://store/apps"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: android.content.ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun AppPickerScreen(
    initiallySelected: Set<String>,
    onDone: (Set<String>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var picked by remember { mutableStateOf(initiallySelected) }

    val allApps = remember {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(0)
            .filter { app: ApplicationInfo -> pm.getLaunchIntentForPackage(app.packageName) != null }
            .map { app -> app.packageName to pm.getApplicationLabel(app).toString() }
            .sortedBy { it.second.lowercase() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Uygulama Ekle") },
            navigationIcon = {
                TextButton(onClick = onCancel) { Text("İptal") }
            },
            actions = {
                TextButton(onClick = { onDone(picked) }) { Text("Kaydet") }
            }
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(allApps) { (packageName, label) ->
                val isChecked = picked.contains(packageName)
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = { Text(packageName, fontSize = 11.sp) },
                    trailingContent = {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                picked = if (checked) picked + packageName else picked - packageName
                            }
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

private fun Modifier.clickableLaunch(
    context: Context,
    packageName: String
): Modifier = this.then(
    Modifier.pointerInput(packageName) {
        detectTapGestures(
            onTap = {
                context.packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        )
    }
)

@Composable
private fun GalleryTab() {
    val context = LocalContext.current
    var photos by remember { mutableStateOf(PhotoVault.listPhotos(context)) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var viewerFile by remember { mutableStateOf<File?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photos = PhotoVault.listPhotos(context)
        } else {
            pendingFile?.delete()
        }
        pendingFile = null
    }

    if (viewerFile != null) {
        val file = viewerFile!!
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val bitmap = remember(file.path) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            TextButton(
                onClick = { viewerFile = null },
                modifier = Modifier.padding(16.dp)
            ) { Text("Kapat", color = Color.White) }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = {
                val file = PhotoVault.newPhotoFile(context)
                pendingFile = file
                val uri = PhotoVault.uriFor(context, file)
                takePicture.launch(uri)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text("\uD83D\uDCF7 Fotoğraf Çek")
        }

        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Henüz fotoğraf yok.\nÇektiğin fotoğraflar yalnızca bu uygulamada,\ntelefonun normal galerisinde görünmeden saklanır.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp)
            ) {
                gridItems(photos) { file ->
                    val bitmap = remember(file.path) {
                        BitmapFactory.decodeFile(file.path)?.asImageBitmap()
                    }
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEEEEEE))
                            .pointerInput(file.path) {
                                detectTapGestures(onTap = { viewerFile = file })
                            }
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    vaultTitle: String,
    onVaultTitleChange: (String) -> Unit
) {
    val context = LocalContext.current
    var titleField by remember { mutableStateOf(vaultTitle) }
    var selectedDisguise by remember { mutableStateOf(IconDisguise.current(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Gizli alan adı", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = titleField,
            onValueChange = {
                titleField = it
                onVaultTitleChange(it)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Ana ekran ikonu / adı", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text(
            "Telefonun ana ekranında/uygulama çekmecesinde görünen ikon ve isim.",
            color = Color.Gray,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        DISGUISES.forEach { disguise ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(disguise.aliasSuffix) {
                        detectTapGestures(onTap = { selectedDisguise = disguise.aliasSuffix })
                    }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = selectedDisguise == disguise.aliasSuffix,
                    onClick = { selectedDisguise = disguise.aliasSuffix }
                )
                Text(disguise.displayLabel)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                IconDisguise.apply(context, selectedDisguise)
                Toast.makeText(
                    context,
                    "Uygulandı. Ana ekran ikonunun güncellenmesi birkaç saniye sürebilir.",
                    Toast.LENGTH_LONG
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Uygula")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Not: rastgele bir fotoğrafı doğrudan ana ekran ikonu yapmak derleme zamanı " +
                "gerektirir; burada birkaç hazır kılık arasında anında geçiş yapabilirsin.",
            color = Color.Gray,
            fontSize = 11.sp
        )
    }
}
