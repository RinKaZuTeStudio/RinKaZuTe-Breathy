package breathy.com.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breathy.com.BreathyApplication
import breathy.com.ui.theme.BreathyBorders
import breathy.com.ui.theme.BreathyPalette
import breathy.com.ui.theme.themeBgPrimary
import breathy.com.ui.theme.themeTextPrimary
import breathy.com.utils.s
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Payment / Payout Setup — Settings → Payment / Payout Setup.
 *
 * PayPal ONLY. The single piece of information collected is the user's
 * PayPal email address, used exclusively for prize payout / gift-card
 * delivery for challenge events. No passwords, no cards, no bank accounts,
 * no other payout providers (spec section K).
 *
 * Storage: `users/{uid}` fields `payoutMethod` / `payoutEmail` /
 * `payoutUpdatedAt` (owner-writable per Firestore rules).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayoutSetupScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as? BreathyApplication ?: return
    val auth = app.appModule.firebaseAuth
    val firestore = app.appModule.firestore

    var email by remember { mutableStateOf("") }
    var loadedEmail by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var savedTick by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ── Load the existing payout email (if any) ────────────────────────────
    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            isLoading = false
            errorMessage = s("Sign in to manage your payout settings.", "سجّل الدخول لإدارة إعدادات الدفع.")
            return@LaunchedEffect
        }
        try {
            val doc = firestore.collection("users").document(uid).get().await()
            val stored = doc.getString("payoutEmail")
            if (!stored.isNullOrBlank()) {
                loadedEmail = stored
                email = stored
            }
        } catch (e: Exception) {
            Timber.w(e, "PayoutSetup: failed to load saved PayPal email")
        }
        isLoading = false
    }

    LaunchedEffect(savedTick) {
        if (savedTick) {
            kotlinx.coroutines.delay(2500)
            savedTick = false
        }
    }

    val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val canSave = emailValid && !isSaving && !isLoading && email.trim() != loadedEmail

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = s("Payment / Payout Setup", "إعدادات الدفع"),
                        fontWeight = FontWeight.Bold,
                        color = themeTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = themeTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeBgPrimary,
                    titleContentColor = themeTextPrimary
                )
            )
        },
        containerColor = themeBgPrimary
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // ── Payment method card — PayPal only, by design ────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BreathyPalette.pureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BreathyBorders.subtle
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = s("Payment Method", "طريقة الدفع"),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = BreathyPalette.textPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    BreathyPalette.veryLightSage,
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🅿️", fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "PayPal",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = BreathyPalette.darkBotanical
                            )
                            Text(
                                text = s("The only supported payout method", "طريقة الدفع الوحيدة المدعومة"),
                                style = MaterialTheme.typography.bodySmall,
                                color = BreathyPalette.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── PayPal email form ────────────────────────────────────────
            Text(
                text = s("PayPal Email", "بريد الدفع (PayPal)"),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = BreathyPalette.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = s(
                    "Used only to send your event prize payouts and gift cards. We never ask for your PayPal password.",
                    "يُستخدم فقط لإرسال جوائز الفعاليات وبطاقات الهدايا. لا نطلب كلمة مرور PayPal أبداً."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = BreathyPalette.textSecondary
            )

            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = BreathyPalette.naturalGreen
                    )
                }
            } else {
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("your-paypal@email.com") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.AlternateEmail,
                            contentDescription = null,
                            tint = BreathyPalette.naturalGreen
                        )
                    },
                    isError = email.isNotBlank() && !emailValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BreathyPalette.naturalGreen,
                        focusedContainerColor = BreathyPalette.pureWhite,
                        unfocusedContainerColor = BreathyPalette.pureWhite
                    )
                )

                if (email.isNotBlank() && !emailValid) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = s("Enter a valid email address.", "أدخل بريداً إلكترونياً صالحاً."),
                        style = MaterialTheme.typography.labelSmall,
                        color = BreathyPalette.error
                    )
                }

                errorMessage?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = BreathyPalette.error
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        val uid = auth.currentUser?.uid
                        if (uid == null) {
                            errorMessage = s("Sign in to manage your payout settings.", "سجّل الدخول لإدارة إعدادات الدفع.")
                            return@Button
                        }
                        isSaving = true
                        val cleanEmail = email.trim()
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                firestore.collection("users").document(uid)
                                    .set(
                                        mapOf(
                                            "payoutMethod" to "paypal",
                                            "payoutEmail" to cleanEmail,
                                            "payoutUpdatedAt" to com.google.firebase.Timestamp.now()
                                        ),
                                        SetOptions.merge()
                                    )
                                    .await()
                                loadedEmail = cleanEmail
                                savedTick = true
                                Timber.i("PayoutSetup: PayPal email saved for %s", uid)
                            } catch (e: Exception) {
                                Timber.e(e, "PayoutSetup: failed to save PayPal email")
                                errorMessage = s(
                                    "Could not save right now — check your connection and try again.",
                                    "تعذّر الحفظ الآن — تحقق من اتصالك وحاول مجدداً."
                                )
                            }
                            isSaving = false
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BreathyPalette.deepForest,
                        contentColor = BreathyPalette.warmWhite
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = BreathyPalette.warmWhite
                        )
                    } else {
                        Text(text = s("Save", "حفظ"), fontWeight = FontWeight.Bold)
                    }
                }

                if (savedTick) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = BreathyPalette.naturalGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = s("PayPal email saved — your prize payouts will be sent here.", "تم حفظ بريد PayPal — ستُرسَل جوائزك إلى هنا."),
                            style = MaterialTheme.typography.bodySmall,
                            color = BreathyPalette.textSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Data-use disclosure ─────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BreathyPalette.veryLightSage)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = s("How your PayPal email is used", "كيف نستخدم بريد PayPal"),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = BreathyPalette.darkBotanical
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = s(
                            "Event prizes include PayPal gift cards. If you win, the Breathy team sends the gift card to this email address. It is stored securely, used only for prize delivery, and never shared with other players.",
                            "تشمل جوائز الفعاليات بطاقات هدايا PayPal. إذا فزت، يرسل فريق Breathy بطاقة الهدايا إلى هذا البريد الإلكتروني. يُخزَّن بشكل آمن، ويُستخدم فقط لتسليم الجوائز، ولا يُشارك أبداً مع اللاعبين الآخرين."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = BreathyPalette.textSecondary
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
