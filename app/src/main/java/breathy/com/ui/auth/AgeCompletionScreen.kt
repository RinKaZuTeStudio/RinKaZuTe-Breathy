package breathy.com.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import breathy.com.data.repository.UserRepository
import breathy.com.utils.s
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun AgeCompletionScreen(
    userRepository: UserRepository,
    onNavigateToHome: () -> Unit
) {
    var age by remember { mutableIntStateOf(30) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasSaved by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            errorMessage = s(
                "You are not signed in. Please log in again.",
                "أنت غير مسجل الدخول. يرجى تسجيل الدخول مرة أخرى."
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🌿",
            style = MaterialTheme.typography.displayMedium
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = s("One last thing", "أمر أخير فقط"),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = s(
                "Please confirm your age to complete your profile. This is asked only once.",
                "يرجى تأكيد عمرك لإكمال ملفك الشخصي. يُطلب هذا مرة واحدة فقط."
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (!isSaving) {
                        age = (age - 1).coerceAtLeast(10)
                        errorMessage = null
                    }
                },
                enabled = !isSaving && age > 10,
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "−",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            Card(
                modifier = Modifier.width(140.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = age.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = s("years", "سنة"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    if (!isSaving) {
                        age = (age + 1).coerceAtMost(120)
                        errorMessage = null
                    }
                },
                enabled = !isSaving && age < 120,
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }

        errorMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                if (isSaving || hasSaved) return@Button

                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser

                if (currentUser == null) {
                    errorMessage = s(
                        "You are not signed in. Please log in again.",
                        "أنت غير مسجل الدخول. يرجى تسجيل الدخول مرة أخرى."
                    )
                    return@Button
                }

                isSaving = true
                errorMessage = null

                val uid = currentUser.uid

                scope.launch {
                    userRepository.updateAge(uid, age)
                        .onSuccess {
                            hasSaved = true
                            isSaving = false

                            Timber.i(
                                "AgeCompletionScreen: age saved successfully for uid=%s",
                                uid
                            )

                            onNavigateToHome()
                        }
                        .onFailure { e ->
                            Timber.e(
                                e,
                                "AgeCompletionScreen: failed to save age for uid=%s",
                                uid
                            )

                            isSaving = false
                            errorMessage = s(
                                "Could not save your age. Please try again.",
                                "تعذر حفظ عمرك. يرجى المحاولة مرة أخرى."
                            )
                        }
                }
            },
            enabled = !isSaving && !hasSaved,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = s(
                        "Continue my journey",
                        "أكمل رحلتي"
                    )
                )
            }
        }
    }
}
