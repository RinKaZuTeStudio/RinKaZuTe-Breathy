package breathy.com.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breathy.com.data.repository.SafetyRepository
import breathy.com.ui.theme.*
import breathy.com.utils.s
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * UGC safety sheet (spec section 30) — report a user/post/message and block
 * a user. Used from chat, public profiles and community posts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportBlockSheet(
    targetType: String,
    targetId: String,
    safetyRepository: SafetyRepository,
    onDismiss: () -> Unit,
    onBlocked: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var selectedReason by remember { mutableStateOf(SafetyRepository.REPORT_REASONS.first()) }
    var details by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var confirmingBlock by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val reportTitle = when (targetType) {
        SafetyRepository.TARGET_POST -> s("Report this post", "الإبلاغ عن هذا المنشور")
        SafetyRepository.TARGET_MESSAGE -> s("Report this message", "الإبلاغ عن هذه الرسالة")
        else -> s("Report this user", "الإبلاغ عن هذا المستخدم")
    }
    val reviewNote = when (targetType) {
        SafetyRepository.TARGET_POST ->
            s(
                "Thank you for keeping Breathy safe. Our moderators will review this post.",
                "شكراً للحفاظ على أمان Breathy. سيراجع فريق الإشراف هذا المنشور."
            )
        SafetyRepository.TARGET_MESSAGE ->
            s(
                "Thank you for keeping Breathy safe. Our moderators will review this message.",
                "شكراً للحفاظ على أمان Breathy. سيراجع فريق الإشراف هذه الرسالة."
            )
        else ->
            s(
                "Thank you for keeping Breathy safe. Our moderators will review this user.",
                "شكراً للحفاظ على أمان Breathy. سيراجع فريق الإشراف هذا المستخدم."
            )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            if (done) {
                Text(
                    text = s("✅ Report submitted", "✅ تم إرسال البلاغ"),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = themeTextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = reviewNote,
                    style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(s("Done", "تم"))
                }
                return@Column
            }

            Text(
                text = reportTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = themeTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = s(
                    "Tell us what's wrong. Reports are private and reviewed by moderators.",
                    "أخبرنا بما حدث. البلاغات خاصة ويطّلع عليها فريق الإشراف."
                ),
                style = MaterialTheme.typography.bodySmall.copy(color = themeTextSecondary)
            )
            Spacer(modifier = Modifier.height(10.dp))

            SafetyRepository.REPORT_REASONS.forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedReason == reason,
                        onClick = { selectedReason = reason },
                        colors = RadioButtonDefaults.colors(selectedColor = NaturalGreen)
                    )
                    Text(
                        text = reasonLabel(reason),
                        style = MaterialTheme.typography.bodyMedium.copy(color = themeTextPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = details,
                onValueChange = { if (it.length <= 300) details = it },
                placeholder = { Text(s("Additional details (optional)", "تفاصيل إضافية (اختياري)")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp)
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.error
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (submitting) return@Button
                        submitting = true
                        error = null
                        scope.launch {
                            try {
                                safetyRepository.report(
                                    targetType = targetType,
                                    targetId = targetId,
                                    reason = selectedReason,
                                    details = details.ifBlank { null }
                                ).getOrThrow()
                                done = true
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.e(e, "report failed")
                                error = s("Couldn't submit the report. Try again.", "تعذّر إرسال البلاغ. حاول مجدداً.")
                            } finally {
                                submitting = false
                            }
                        }
                    },
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NaturalGreen),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp).height(18.dp),
                            strokeWidth = 2.dp,
                            color = PureWhite
                        )
                    } else {
                        Text(s("Submit report", "إرسال البلاغ"), fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (targetType == SafetyRepository.TARGET_USER) {
                Spacer(modifier = Modifier.height(10.dp))
                if (!confirmingBlock) {
                    TextButton(onClick = { confirmingBlock = true }) {
                        Text(
                            text = s("🚫 Block this user", "🚫 حظر هذا المستخدم"),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = s("Block this user?", "حظر هذا المستخدم؟"),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = s(
                                    "They won't be able to message you, and their messages and posts will be hidden from you. You can unblock anytime in chat settings.",
                                    "لن يتمكّن من مراسلتك، وستُخفى رسائله ومنشوراته عنك. يمكنك إلغاء الحظر في أي وقت من إعدادات الدردشة."
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(color = themeTextSecondary)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { confirmingBlock = false }) {
                                    Text(s("Cancel", "إلغاء"))
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                safetyRepository.blockUser(targetId).getOrThrow()
                                                onBlocked()
                                                onDismiss()
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                Timber.e(e, "block failed")
                                                error = s("Couldn't block this user. Try again.", "تعذّر حظر هذا المستخدم. حاول مجدداً.")
                                                confirmingBlock = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(s("Block", "حظر"), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Banner shown inside a chat when the other user is blocked.
 */
@Composable
fun BlockedUserBanner(
    onUnblock: () -> Unit,
    unblocking: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SoftSand.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = s("You blocked this user", "لقد حظرت هذا المستخدم"),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = themeTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = s("They can't message you and their messages are hidden.", "لا يمكنه مراسلتك ورسائله مخفية."),
                style = MaterialTheme.typography.bodySmall.copy(color = themeTextSecondary)
            )
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onUnblock, enabled = !unblocking) {
                Text(
                    text = if (unblocking) s("Unblocking…", "جارٍ إلغاء الحظر…") else s("Unblock", "إلغاء الحظر"),
                    color = NaturalGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Localized display label for a report reason. The canonical reason string
 * (SafetyRepository.REPORT_REASONS) is still what gets stored in Firestore —
 * only the visible label is translated.
 */
private fun reasonLabel(reason: String): String = when (reason) {
    "Spam or scam" -> s("Spam or scam", "إزعاج أو احتيال")
    "Harassment or bullying" -> s("Harassment or bullying", "تحرّش أو تنمّر")
    "Inappropriate content" -> s("Inappropriate content", "محتوى غير لائق")
    "Misinformation" -> s("Misinformation", "معلومات مضلّلة")
    "Self-harm concern" -> s("Self-harm concern", "قلق من إيذاء النفس")
    "Other" -> s("Other", "أخرى")
    else -> reason
}
