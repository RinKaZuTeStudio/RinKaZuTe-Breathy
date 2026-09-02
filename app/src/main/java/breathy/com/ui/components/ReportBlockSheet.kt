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

    val targetLabel = when (targetType) {
        SafetyRepository.TARGET_POST -> "post"
        SafetyRepository.TARGET_MESSAGE -> "message"
        else -> "user"
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
                    text = "✅ Report submitted",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = themeTextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Thank you for keeping Breathy safe. Our moderators will review this $targetLabel.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
                return@Column
            }

            Text(
                text = "Report this $targetLabel",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = themeTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tell us what's wrong. Reports are private and reviewed by moderators.",
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
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium.copy(color = themeTextPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = details,
                onValueChange = { if (it.length <= 300) details = it },
                placeholder = { Text("Additional details (optional)") },
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
                                error = "Couldn't submit the report. Try again."
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
                        Text("Submit report", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (targetType == SafetyRepository.TARGET_USER) {
                Spacer(modifier = Modifier.height(10.dp))
                if (!confirmingBlock) {
                    TextButton(onClick = { confirmingBlock = true }) {
                        Text(
                            text = "🚫 Block this user",
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
                                text = "Block this user?",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "They won't be able to message you, and their messages and posts will be hidden from you. You can unblock anytime in chat settings.",
                                style = MaterialTheme.typography.bodySmall.copy(color = themeTextSecondary)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { confirmingBlock = false }) {
                                    Text("Cancel")
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
                                                error = "Couldn't block this user. Try again."
                                                confirmingBlock = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Block", fontWeight = FontWeight.Bold)
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
                text = "You blocked this user",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = themeTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "They can't message you and their messages are hidden.",
                style = MaterialTheme.typography.bodySmall.copy(color = themeTextSecondary)
            )
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onUnblock, enabled = !unblocking) {
                Text(
                    text = if (unblocking) "Unblocking…" else "Unblock",
                    color = NaturalGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
