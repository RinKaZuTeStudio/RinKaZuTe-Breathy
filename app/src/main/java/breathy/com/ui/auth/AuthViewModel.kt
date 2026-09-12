    /**
     * ✅ FIX v1.0.30 — retryPendingProfileUpload
     *
     * المشكلة السابقة: كانت تستخدم batch.set(userRef, effectiveUserMap) بدون
     * SetOptions.merge()، مما يحذف createdAt من المستند عند الكتابة الكاملة.
     * قواعد Firestore v9 ترفض هذا (identity anchor moved)، فيفشل الرفع 3 مرات،
     * ثم يُعاد توجيه المستخدم إلى Onboarding في كل مرة بعد التحديث.
     *
     * الإصلاح:
     * 1. استخدام SetOptions.merge() دائماً
     * 2. حذف createdAt من الـ map (لا نكتبه أبداً عند التحديث)
     * 3. حذف email من الـ map إذا كان موجوداً في المستند
     * 4. إضافة import لـ SetOptions
     */
    private fun retryPendingProfileUpload(userId: String) {
        val pending = onboardingLocalStore.readPendingProfile(userId) ?: return
        viewModelScope.launch {
            try {
                val (userMap, publicMap) = onboardingLocalStore.buildFirestoreMaps(pending)
                repeat(3) { attempt ->
                    try {
                        withTimeoutOrNull(10_000L) {
                            val userRef = firestore.collection("users").document(userId)
                            val existing = try {
                                userRef.get().await()
                            } catch (e: Exception) { null }

                            // ✅ FIX: لا تحذف createdAt أبداً عند التحديث
                            val effectiveUserMap = userMap.toMutableMap().apply {
                                if (existing?.exists() == true) {
                                    remove("createdAt")
                                    if (existing.contains("email")) {
                                        remove("email")
                                    }
                                }
                            }

                            val batch = firestore.batch()
                            // ✅ FIX الأساسي: SetOptions.merge() بدلاً من set() الكامل
                            batch.set(userRef, effectiveUserMap, SetOptions.merge())
                            batch.set(
                                firestore.collection("publicProfiles").document(userId),
                                publicMap,
                                SetOptions.merge()
                            )
                            batch.commit().await()
                        } ?: throw java.util.concurrent.TimeoutException()
                        Timber.i("$TAG: pending onboarding profile uploaded for uid=%s (attempt %d)", userId, attempt + 1)
                        onboardingLocalStore.clearPendingProfile(userId)
                        return@launch
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "$TAG: pending upload attempt %d failed for uid=%s", attempt + 1, userId)
                    }
                    kotlinx.coroutines.delay(5_000L * (attempt + 1))
                }
                Timber.w("$TAG: pending profile upload still failing for uid=%s — will retry next launch", userId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "$TAG: pending profile retry crashed for uid=%s", userId)
            }
        }
    }
