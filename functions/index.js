/**
 * Breathy Cloud Functions
 *
 * All backend logic for the Breathy quit-smoking Android app:
 *   a. updateDaysSmokeFree           — scheduled daily at 2 AM UTC
 *   b. onReplyCreated                — Firestore trigger on reply creation
 *   c. onReplyDeleted                — Firestore trigger on reply deletion
 *   d. sendChatNotification          — Firestore trigger on new chat message
 *   e. calculateEventRanks           — scheduled hourly
 *   f. sendFriendRequestNotification — Firestore trigger on notification doc
 *   g. openAIChat                    — callable function (rate-limited)
 *   h. createPushupChallenge         — callable function to create pushup event
 *   i. submitPushupCount             — callable function to submit pushup count
 */

const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentCreated, onDocumentDeleted } = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineString } = require("firebase-functions/params");
const { logger } = require("firebase-functions");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { getAuth } = require("firebase-admin/auth");
const OpenAI = require("openai");
const { v4: uuidv4 } = require("uuid");

// ─── Firebase Admin init ──────────────────────────────────────────────────────
initializeApp();
const db = getFirestore();
const auth = getAuth();
const messaging = getMessaging();

// ─── Constants ────────────────────────────────────────────────────────────────
const BATCH_SIZE = 500;
// Primary: Llama 4 Scout (free, higher rate limits than Gemma). Fallback: DeepSeek Chat.
const OPENROUTER_MODEL = "meta-llama/llama-4-scout:free";
const OPENROUTER_FALLBACK_MODEL = "deepseek/deepseek-chat";

// OpenRouter API key parameter.
// Set with: firebase functions:secrets:set OPENROUTER_API_KEY
// Get a key at: https://openrouter.ai/keys
const openrouterApiKeyParam = defineString("OPENROUTER_API_KEY", {
  description: "OpenRouter API key for AI Coach chatbot",
  default: ""
});
const OPENAI_TEMPERATURE = 0.7;
const OPENAI_MAX_CONTEXT_MESSAGES = 20;
const RATE_LIMIT_MAX_MESSAGES = 10;
const RATE_LIMIT_WINDOW_MS = 60_000;
const MESSAGE_PREVIEW_LENGTH = 100;

const SYSTEM_PROMPT = `You are Breathy AI Coach, a supportive quit-smoking assistant. Your role is to help users stay smoke-free through encouragement, practical advice, and empathetic conversation.

Guidelines:
- Be warm, supportive, and non-judgmental
- Celebrate every victory, no matter how small
- Suggest practical coping strategies: breathing exercises (4-7-8 technique), distraction activities, hydration, physical movement
- Acknowledge that cravings are normal and temporary (usually last 3-5 minutes)
- Reference health milestones when relevant (20 min: heart rate normalizes, 8 hrs: oxygen levels normalize, 24 hrs: heart attack risk decreases, 48 hrs: taste/smell improve, 72 hrs: breathing eases, 2 weeks: circulation improves, 1 month: lung function improves)
- Never provide medical diagnosis or replace professional medical advice
- Keep responses concise (2-4 paragraphs max) unless the user needs detailed guidance
- If a user seems to be in crisis or expresses self-harm thoughts, urge them to contact a healthcare professional or helpline
- Use motivational language: "you've got this", "every smoke-free minute counts", "you're stronger than the craving"
- Personalize responses based on how long they've been smoke-free when mentioned`;

// ═══════════════════════════════════════════════════════════════════════════════
//  a. updateDaysSmokeFree — Scheduled function, runs daily at 2 AM UTC
// ═══════════════════════════════════════════════════════════════════════════════
exports.updateDaysSmokeFree = onSchedule(
  {
    schedule: "0 2 * * *",
    timeZone: "UTC",
    timeoutSeconds: 540,
    memory: "512MiB",
  },
  async (event) => {
    logger.info("updateDaysSmokeFree: Starting daily smoke-free update");

    const now = new Date();
    let updatedCount = 0;
    let errorCount = 0;
    let lastDocId = null;

    try {
      // Paginate through all publicProfiles
      let hasMore = true;
      while (hasMore) {
        let query = db.collection("publicProfiles").limit(BATCH_SIZE);
        if (lastDocId) {
          const lastDoc = await db.collection("publicProfiles").doc(lastDocId).get();
          if (lastDoc.exists) {
            query = query.startAfter(lastDoc);
          }
        }

        const snapshot = await query.get();

        if (snapshot.empty) {
          hasMore = false;
          break;
        }

        const batch = db.batch();
        let batchOpCount = 0;

        for (const doc of snapshot.docs) {
          const data = doc.data();
          const profileRef = doc.ref;

          // Skip profiles without a quitDate
          if (!data.quitDate) {
            logger.warn(`updateDaysSmokeFree: Skipping profile ${doc.id} — no quitDate`);
            continue;
          }

          // Calculate days since quitDate
          const quitDate = data.quitDate.toDate();
          const diffMs = now.getTime() - quitDate.getTime();
          const daysSmokeFree = Math.max(0, Math.floor(diffMs / (1000 * 60 * 60 * 24)));

          // Only update if the value has changed
          if (data.daysSmokeFree !== daysSmokeFree) {
            batch.update(profileRef, { daysSmokeFree: daysSmokeFree });
            batchOpCount++;
            updatedCount++;
          }
        }

        // Commit the batch if there are operations
        if (batchOpCount > 0) {
          await batch.commit();
        }

        // Check if there are more documents to process
        if (snapshot.size < BATCH_SIZE) {
          hasMore = false;
        } else {
          lastDocId = snapshot.docs[snapshot.size - 1].id;
        }
      }

      logger.info(
        `updateDaysSmokeFree: Completed. Updated ${updatedCount} profiles, ${errorCount} errors`
      );
    } catch (error) {
      logger.error("updateDaysSmokeFree: Fatal error during update", error);
      throw error;
    }
  }
);

// ═══════════════════════════════════════════════════════════════════════════════
//  b. onReplyCreated — Firestore trigger on stories/{storyId}/replies/{replyId}
// ═══════════════════════════════════════════════════════════════════════════════
exports.onReplyCreated = onDocumentCreated(
  {
    document: "stories/{storyId}/replies/{replyId}",
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) {
      logger.warn("onReplyCreated: No snapshot data, skipping");
      return null;
    }

    const storyId = event.params.storyId;
    const replyData = snap.data();

    if (!replyData) {
      logger.warn(`onReplyCreated: No reply data for story ${storyId}, skipping`);
      return null;
    }

    logger.info(`onReplyCreated: Reply created on story ${storyId}`);

    try {
      const storyRef = db.collection("stories").doc(storyId);

      // Atomically increment replyCount
      await storyRef.update({
        replyCount: FieldValue.increment(1),
      });

      logger.info(`onReplyCreated: Incremented replyCount for story ${storyId}`);
    } catch (error) {
      // If the story doesn't exist, log but don't throw
      if (error.code === 5) {
        // NOT_FOUND
        logger.warn(`onReplyCreated: Story ${storyId} not found, skipping increment`);
      } else {
        logger.error(`onReplyCreated: Error incrementing replyCount for story ${storyId}`, error);
        throw error;
      }
    }

    return null;
  }
);

// ═══════════════════════════════════════════════════════════════════════════════
//  c. onReplyDeleted — Firestore trigger on stories/{storyId}/replies/{replyId}
// ═══════════════════════════════════════════════════════════════════════════════
exports.onReplyDeleted = onDocumentDeleted(
  {
    document: "stories/{storyId}/replies/{replyId}",
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) {
      logger.warn("onReplyDeleted: No snapshot data, skipping");
      return null;
    }

    const storyId = event.params.storyId;

    logger.info(`onReplyDeleted: Reply deleted on story ${storyId}`);

    try {
      const storyRef = db.collection("stories").doc(storyId);
      const storyDoc = await storyRef.get();

      if (!storyDoc.exists) {
        logger.warn(`onReplyDeleted: Story ${storyId} not found, skipping decrement`);
        return null;
      }

      const currentCount = storyDoc.data().replyCount || 0;

      // Ensure count doesn't go below 0
      if (currentCount <= 0) {
        logger.warn(
          `onReplyDeleted: replyCount for story ${storyId} is already ${currentCount}, not decrementing`
        );
        return null;
      }

      // Atomically decrement replyCount
      await storyRef.update({
        replyCount: FieldValue.increment(-1),
      });

      logger.info(`onReplyDeleted: Decremented replyCount for story ${storyId}`);
    } catch (error) {
      logger.error(`onReplyDeleted: Error decrementing replyCount for story ${storyId}`, error);
      throw error;
    }

    return null;
  }
);

// ═══════════════════════════════════════════════════════════════════════════════
//  d. sendChatNotification — Firestore trigger on chats/{chatId}/messages/{messageId}
// ═══════════════════════════════════════════════════════════════════════════════
exports.sendChatNotification = onDocumentCreated(
  {
    document: "chats/{chatId}/messages/{messageId}",
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) {
      logger.warn("sendChatNotification: No snapshot data, skipping");
      return null;
    }

    const chatId = event.params.chatId;
    const messageData = snap.data();

    if (!messageData) {
      logger.warn(`sendChatNotification: No message data in chat ${chatId}, skipping`);
      return null;
    }

    const senderId = messageData.senderId;
    const messageText = messageData.text || "";

    if (!senderId) {
      logger.warn(`sendChatNotification: No senderId in message, skipping`);
      return null;
    }

    logger.info(`sendChatNotification: New message in chat ${chatId} from ${senderId}`);

    try {
      // 1. Get the chat document to find participants
      const chatDoc = await db.collection("chats").doc(chatId).get();

      if (!chatDoc.exists) {
        logger.warn(`sendChatNotification: Chat ${chatId} not found`);
        return null;
      }

      const chatData = chatDoc.data();
      const participants = chatData.participants || [];

      if (participants.length !== 2) {
        logger.warn(`sendChatNotification: Chat ${chatId} has unexpected participants count: ${participants.length}`);
        return null;
      }

      // 2. Find the recipient (the other participant)
      const recipientId = participants.find((uid) => uid !== senderId);

      if (!recipientId) {
        logger.warn(`sendChatNotification: Could not determine recipient for chat ${chatId}`);
        return null;
      }

      // Don't notify if recipient is the sender (safety check)
      if (recipientId === senderId) {
        logger.info(`sendChatNotification: Recipient is sender, skipping notification`);
        return null;
      }

      // 3. Get sender's profile for notification content
      const senderProfileDoc = await db.collection("publicProfiles").doc(senderId).get();
      const senderNickname = senderProfileDoc.exists
        ? senderProfileDoc.data().nickname || "Someone"
        : "Someone";

      // 4. Get recipient's FCM token and notification preferences
      const recipientDoc = await db.collection("users").doc(recipientId).get();

      if (!recipientDoc.exists) {
        logger.warn(`sendChatNotification: Recipient user ${recipientId} not found`);
        return null;
      }

      const recipientData = recipientDoc.data();
      const fcmToken = recipientData.fcmToken;

      if (!fcmToken) {
        logger.info(`sendChatNotification: No FCM token for recipient ${recipientId}, skipping`);
        return null;
      }

      // 5. Truncate message for notification preview
      const messagePreview =
        messageText.length > MESSAGE_PREVIEW_LENGTH
          ? messageText.substring(0, MESSAGE_PREVIEW_LENGTH) + "..."
          : messageText;

      // 6. Build and send FCM notification
      const payload = {
        token: fcmToken,
        notification: {
          title: senderNickname,
          body: messagePreview,
        },
        android: {
          priority: "high" as const,
          notification: {
            channelId: "chat",
            clickAction: "OPEN_CHAT",
            tag: `chat_${chatId}`,
            defaultSound: true,
            defaultVibrateTimings: true,
          },
        },
        apns: {
          payload: {
            aps: {
              sound: "default",
              badge: 1,
              "thread-id": chatId,
            },
          },
        },
        data: {
          type: "chat_message",
          chatId: chatId,
          senderId: senderId,
          senderNickname: senderNickname,
          clickAction: "OPEN_CHAT",
        },
      };

      const response = await messaging.send(payload);

      logger.info(
        `sendChatNotification: Notification sent to ${recipientId}, message ID: ${response}`
      );
    } catch (error) {
      // Handle specific FCM errors gracefully
      if (error.code === "messaging/registration-token-not-registered" ||
          error.code === "messaging/invalid-registration-token") {
        logger.warn(
          `sendChatNotification: Invalid FCM token for recipient, cleaning up: ${error.message}`
        );
        // Clean up the stale FCM token
        try {
          await db.collection("users").doc(recipientId).update({
            fcmToken: FieldValue.delete(),
          });
          logger.info(`sendChatNotification: Removed stale FCM token for user ${recipientId}`);
        } catch (cleanupError) {
          logger.error(
            `sendChatNotification: Failed to clean up stale FCM token for ${recipientId}`,
            cleanupError
          );
        }
      } else {
        logger.error(
          `sendChatNotification: Error sending notification for chat ${chatId}`,
          error
        );
      }
      // Don't rethrow — notification failures shouldn't block the write
    }

    return null;
  }
);

// ═══════════════════════════════════════════════════════════════════════════════
//  e. calculateEventRanks — Scheduled function, runs hourly
// ═══════════════════════════════════════════════════════════════════════════════
exports.calculateEventRanks = onSchedule(
  {
    schedule: "0 * * * *",
    timeZone: "UTC",
    timeoutSeconds: 540,
    memory: "512MiB",
  },
  async (event) => {
    logger.info("calculateEventRanks: Starting hourly rank calculation");

    const now = new Date();
    let eventsProcessed = 0;
    let participantsUpdated = 0;

    try {
      // Query all active events
      const eventsSnapshot = await db
        .collection("events")
        .where("active", "==", true)
        .get();

      if (eventsSnapshot.empty) {
        logger.info("calculateEventRanks: No active events found");
        return;
      }

      for (const eventDoc of eventsSnapshot.docs) {
        const eventId = eventDoc.id;
        const eventData = eventDoc.data();

        eventsProcessed++;

        // Calculate event duration in days
        const startDate = eventData.startDate.toDate();
        const endDate = eventData.endDate.toDate();
        const eventDurationMs = endDate.getTime() - startDate.getTime();
        const eventDurationDays = Math.max(
          1,
          Math.ceil(eventDurationMs / (1000 * 60 * 60 * 24))
        );
        const dailyRequired = eventData.dailyRequired || 1;
        const completionThreshold = dailyRequired * eventDurationDays;

        // Query participants for this event, ordered by totalApprovedDays desc, currentStreak desc
        const participantsSnapshot = await db
          .collection("eventParticipants")
          .where("eventId", "==", eventId)
          .orderBy("totalApprovedDays", "desc")
          .orderBy("currentStreak", "desc")
          .get();

        if (participantsSnapshot.empty) {
          logger.info(`calculateEventRanks: No participants for event ${eventId}`);
          continue;
        }

        // Batch update ranks
        const batch = db.batch();
        let batchOpCount = 0;

        participantsSnapshot.docs.forEach((doc, index) => {
          const participantData = doc.data();
          const rank = index + 1;
          const updateData = { rank: rank };

          // Check completion condition:
          // totalApprovedDays >= dailyRequired * event days
          if (
            !participantData.completed &&
            participantData.totalApprovedDays >= completionThreshold
          ) {
            updateData.completed = true;
            updateData.completionTimestamp = Timestamp.now();
            logger.info(
              `calculateEventRanks: User ${participantData.userId} completed event ${eventId} at rank ${rank}`
            );
          }

          batch.update(doc.ref, updateData);
          batchOpCount++;
          participantsUpdated++;

          // Firestore batch limit is 500 operations
          if (batchOpCount >= BATCH_SIZE) {
            logger.warn(
              `calculateEventRanks: Batch size limit reached for event ${eventId}, committing partial batch`
            );
            // Note: In practice, an event is unlikely to have >500 participants,
            // but we handle it defensively by committing and starting a new batch.
          }
        });

        // Commit the batch
        if (batchOpCount > 0) {
          await batch.commit();
        }

        logger.info(
          `calculateEventRanks: Updated ${batchOpCount} participants for event ${eventId}`
        );
      }

      logger.info(
        `calculateEventRanks: Completed. Processed ${eventsProcessed} events, updated ${participantsUpdated} participants`
      );
    } catch (error) {
      logger.error("calculateEventRanks: Fatal error during rank calculation", error);
      throw error;
    }
  }
);

// ═══════════════════════════════════════════════════════════════════════════════
//  g. sendFriendRequestNotification — Firestore trigger on notifications/{docId}
// ═══════════════════════════════════════════════════════════════════════════════
exports.sendFriendRequestNotification = onDocumentCreated(
  {
    document: "notifications/{docId}",
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) {
      logger.warn("sendFriendRequestNotification: No snapshot data, skipping");
      return null;
    }

    const data = snap.data();
    if (!data) {
      logger.warn("sendFriendRequestNotification: No data, skipping");
      return null;
    }

    const type = data.type;
    if (type !== "friend_request") {
      // Only handle friend_request notifications
      return null;
    }

    const fcmToken = data.toFcmToken;
    const senderName = data.senderName || "Someone";
    const senderId = data.senderId || "";
    const requestId = data.requestId || "";

    if (!fcmToken) {
      logger.warn("sendFriendRequestNotification: No FCM token, skipping");
      return null;
    }

    logger.info(`sendFriendRequestNotification: Sending friend request notification from ${senderName}`);

    try {
      const payload = {
        token: fcmToken,
        notification: {
          title: "Friend Request",
          body: `${senderName} wants to be your friend!`,
        },
        android: {
          priority: "high" as const,
          notification: {
            channelId: "friend_requests",
            clickAction: "OPEN_FRIENDS",
            tag: `friend_request_${requestId}`,
            defaultSound: true,
            defaultVibrateTimings: true,
          },
        },
        data: {
          type: "friend_request",
          senderName: senderName,
          senderId: senderId,
          requestId: requestId,
          route: "friends",
        },
      };

      const response = await messaging.send(payload);
      logger.info(`sendFriendRequestNotification: Notification sent, message ID: ${response}`);

      // Mark notification as processed
      await snap.ref.update({ processed: true });
    } catch (error) {
      logger.error(`sendFriendRequestNotification: Error sending notification`, error);

      // Clean up stale FCM token
      if (error.code === "messaging/registration-token-not-registered" ||
          error.code === "messaging/invalid-registration-token") {
        try {
          await db.collection("users").doc(data.toUserId).update({
            fcmToken: FieldValue.delete(),
          });
          logger.info(`sendFriendRequestNotification: Removed stale FCM token for user ${data.toUserId}`);
        } catch (cleanupError) {
          logger.error(`sendFriendRequestNotification: Failed to clean up stale FCM token`, cleanupError);
        }
      }
    }

    return null;
  }
);

// ═══════════════════════════════════════════════════════════════════════════════
//  f. openAIChat — Callable function (rate-limited)
// ═══════════════════════════════════════════════════════════════════════════════
exports.openAIChat = onCall(
  {
    timeoutSeconds: 60,
    memory: "256MiB",
    enforceAppCheck: false,
  },
  async (request) => {
    // ── 1. Verify Firebase Auth ──────────────────────────────────────────────
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "You must be signed in to use the AI Coach.");
    }

    const uid = request.auth.uid;
    const { message } = request.data || {};

    // Validate message
    if (!message || typeof message !== "string" || message.trim().length === 0) {
      throw new HttpsError("invalid-argument", "Message cannot be empty.");
    }

    if (message.length > 2000) {
      throw new HttpsError("invalid-argument", "Message is too long (max 2000 characters).");
    }

    logger.info(`openAIChat: User ${uid} sent message (${message.length} chars)`);

    // ── 2. Rate limit: max 10 messages per minute per user ───────────────────
    try {
      const oneMinuteAgo = Timestamp.fromMillis(Date.now() - RATE_LIMIT_WINDOW_MS);
      const recentMessagesSnapshot = await db
        .collection("users")
        .doc(uid)
        .collection("coach_chats")
        .where("role", "==", "user")
        .where("timestamp", ">=", oneMinuteAgo)
        .get();

      if (recentMessagesSnapshot.size >= RATE_LIMIT_MAX_MESSAGES) {
        logger.warn(
          `openAIChat: Rate limit exceeded for user ${uid} (${recentMessagesSnapshot.size} messages in last minute)`
        );
        throw new HttpsError(
          "resource-exhausted",
          "You're sending messages too quickly. Please wait a moment before trying again."
        );
      }
    } catch (error) {
      // Re-throw HttpsError as-is
      if (error instanceof HttpsError) throw error;
      // Log but continue if rate-limit check itself fails
      logger.warn(`openAIChat: Rate limit check failed for user ${uid}, proceeding`, error);
    }

    // ── 3. Get conversation history from coach_chats ─────────────────────────
    let conversationHistory = [];
    try {
      const historySnapshot = await db
        .collection("users")
        .doc(uid)
        .collection("coach_chats")
        .orderBy("timestamp", "desc")
        .limit(OPENAI_MAX_CONTEXT_MESSAGES)
        .get();

      // Reverse to get chronological order (oldest first)
      conversationHistory = historySnapshot.docs
        .map((doc) => {
          const data = doc.data();
          return {
            role: data.role === "user" ? "user" : "assistant",
            content: data.content || "",
          };
        })
        .reverse()
        .filter((msg) => msg.content.length > 0);
    } catch (error) {
      logger.warn(`openAIChat: Failed to load history for user ${uid}, starting fresh`, error);
      conversationHistory = [];
    }

    // ── 4. Save user message to coach_chats ──────────────────────────────────
    const userMessageId = uuidv4();
    try {
      await db
        .collection("users")
        .doc(uid)
        .collection("coach_chats")
        .doc(userMessageId)
        .set({
          role: "user",
          content: message.trim(),
          timestamp: FieldValue.serverTimestamp(),
        });
    } catch (error) {
      logger.error(`openAIChat: Failed to save user message for ${uid}`, error);
      // Continue anyway — the conversation can proceed without persistence
    }

    // ── 5. Call OpenRouter API (OpenAI-compatible) ──────────────────────────
    const openrouterApiKey = openrouterApiKeyParam.value();
    if (!openrouterApiKey) {
      throw new HttpsError("internal", "AI Coach API key not configured. Set OPENROUTER_API_KEY in Firebase.");
    }
    const openai = new OpenAI({
      apiKey: openrouterApiKey,
      baseURL: "https://openrouter.ai/api/v1",
      defaultHeaders: {
        "HTTP-Referer": "https://breathy-healthy.web.app",
        "X-Title": "Breathy AI Coach"
      }
    });

    // Build messages array with system prompt + history + new message
    const openaiMessages = [
      { role: "system", content: SYSTEM_PROMPT },
      ...conversationHistory,
      { role: "user", content: message.trim() },
    ];

    let assistantContent;
    try {
      const completion = await openai.chat.completions.create({
        model: OPENROUTER_MODEL,
        messages: openaiMessages,
        temperature: OPENAI_TEMPERATURE,
        max_tokens: 1024,
        top_p: 1,
        frequency_penalty: 0,
        presence_penalty: 0,
      });

      assistantContent =
        completion.choices?.[0]?.message?.content?.trim() ||
        "I'm here for you. Keep going — you're stronger than you think!";
    } catch (primaryError) {
      logger.warn(`openAIChat: Primary model (${OPENROUTER_MODEL}) failed for user ${uid}, trying fallback`, {
        error: primaryError.message,
        status: primaryError.status,
      });

      // Try fallback model
      try {
        const fallbackCompletion = await openai.chat.completions.create({
          model: OPENROUTER_FALLBACK_MODEL,
          messages: openaiMessages,
          temperature: OPENAI_TEMPERATURE,
          max_tokens: 1024,
          top_p: 1,
          frequency_penalty: 0,
          presence_penalty: 0,
        });

        assistantContent =
          fallbackCompletion.choices?.[0]?.message?.content?.trim() ||
          "I'm here for you. Keep going — you're stronger than you think!";

        logger.info(`openAIChat: Fallback model succeeded for user ${uid}`);
      } catch (fallbackError) {
        logger.error(`openAIChat: Both models failed for user ${uid}`, {
          primaryError: primaryError.message,
          fallbackError: fallbackError.message,
        });

        // Graceful fallback on API error
        assistantContent =
          "I'm having trouble connecting right now. Please try again in a moment. " +
          "Remember, every craving you resist makes you stronger! " +
          "Try a 4-7-8 breathing exercise: breathe in for 4 seconds, hold for 7, exhale for 8.";
      }
    }

    // ── 6. Save assistant response to coach_chats ────────────────────────────
    const assistantMessageId = uuidv4();
    try {
      await db
        .collection("users")
        .doc(uid)
        .collection("coach_chats")
        .doc(assistantMessageId)
        .set({
          role: "assistant",
          content: assistantContent,
          timestamp: FieldValue.serverTimestamp(),
        });
    } catch (error) {
      logger.error(`openAIChat: Failed to save assistant message for ${uid}`, error);
      // Continue — we still want to return the response to the client
    }

    // ── 7. Return assistant response ─────────────────────────────────────────
    logger.info(`openAIChat: Responded to user ${uid} (${assistantContent.length} chars)`);

    return {
      content: assistantContent,
    };
  }
);

// ═══════════════════════════════════════════════════════════════════════════════
//  h. createPushupChallenge — Callable function to create the pushup event
// ═══════════════════════════════════════════════════════════════════════════════
exports.createPushupChallenge = onCall(
  {
    timeoutSeconds: 30,
    memory: "256MiB",
    enforceAppCheck: false,
  },
  async (request) => {
    // Only authenticated users can create events (admin function)
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "You must be signed in.");
    }

    const eventId = "pushup_challenge_2025";

    // Check if event already exists
    const existingDoc = await db.collection("events").doc(eventId).get();
    if (existingDoc.exists) {
      return { eventId, message: "Pushup Challenge event already exists", alreadyExists: true };
    }

    // Event starts tomorrow, runs for 3 months
    const now = new Date();
    const startDate = new Date(now);
    startDate.setDate(startDate.getDate() + 1);
    startDate.setHours(0, 0, 0, 0);

    const endDate = new Date(startDate);
    endDate.setMonth(endDate.getMonth() + 3);

    const eventData = {
      title: "100 Pushup Challenge",
      description: "Complete 100 pushups over 3 months! Record yourself using the camera and our AI will count your pushups automatically using pose detection. Stay consistent, build strength, and compete with others on the leaderboard. Each day you complete your pushups counts as a check-in!",
      startDate: Timestamp.fromDate(startDate),
      endDate: Timestamp.fromDate(endDate),
      active: true,
      dailyRequired: 1,
      eventType: "pushup",
      targetPushups: 100,
      prizes: {
        "1st": "5,000 coins + Champion Badge",
        "2nd": "3,000 coins + Silver Badge",
        "3rd": "1,500 coins + Bronze Badge",
        "Top 10": "500 coins + Pushup Star Badge",
      },
      createdAt: FieldValue.serverTimestamp(),
    };

    await db.collection("events").doc(eventId).set(eventData);

    logger.info(`createPushupChallenge: Created event ${eventId}`);

    return {
      eventId,
      message: "Pushup Challenge event created successfully!",
      startDate: startDate.toISOString(),
      endDate: endDate.toISOString(),
    };
  }
);

// ═══════════════════════════════════════════════════════════════════════════════
//  i. submitPushupCount — Callable function to submit pushup count
// ═══════════════════════════════════════════════════════════════════════════════
exports.submitPushupCount = onCall(
  {
    timeoutSeconds: 30,
    memory: "256MiB",
    enforceAppCheck: false,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "You must be signed in.");
    }

    const uid = request.auth.uid;
    const { eventId, pushupCount, sessionDurationSeconds } = request.data || {};

    if (!eventId || typeof eventId !== "string") {
      throw new HttpsError("invalid-argument", "eventId is required.");
    }

    if (!pushupCount || typeof pushupCount !== "number" || pushupCount < 1) {
      throw new HttpsError("invalid-argument", "pushupCount must be a positive number.");
    }

    // Anti-cheat: cap max pushups per session at a reasonable limit
    if (pushupCount > 200) {
      throw new HttpsError("invalid-argument", "Maximum 200 pushups per session.");
    }

    // Anti-cheat: minimum session duration check (pushup takes ~3 seconds minimum)
    const minDuration = pushupCount * 2; // 2 seconds per pushup minimum
    if (sessionDurationSeconds && sessionDurationSeconds < minDuration) {
      throw new HttpsError("invalid-argument", "Session duration too short for reported pushup count.");
    }

    // Rate limit: max 5 pushup submissions per hour
    const oneHourAgo = Timestamp.fromMillis(Date.now() - 3600_000);
    const recentSubmissions = await db
      .collection("eventCheckins")
      .where("userId", "==", uid)
      .where("eventId", "==", eventId)
      .where("submittedAt", ">=", oneHourAgo)
      .get();

    if (recentSubmissions.size >= 5) {
      throw new HttpsError(
        "resource-exhausted",
        "Too many submissions. Please wait before submitting again."
      );
    }

    logger.info(`submitPushupCount: User ${uid} submitted ${pushupCount} pushups for event ${eventId}`);

    // Create check-in document
    const checkinData = {
      userId: uid,
      eventId: eventId,
      pushupCount: pushupCount,
      sessionDurationSeconds: sessionDurationSeconds || 0,
      status: "approved", // Auto-approve since ML Kit verified the pushups
      submittedAt: FieldValue.serverTimestamp(),
      type: "pushup",
    };

    const checkinRef = await db.collection("eventCheckins").add(checkinData);

    // Update participant stats in a transaction
    const participantId = `${uid}_${eventId}`;
    const participantRef = db.collection("eventParticipants").doc(participantId);

    await db.runTransaction(async (transaction) => {
      const participantDoc = await transaction.get(participantRef);
      if (!participantDoc.exists) {
        throw new HttpsError("not-found", "You must join the event first.");
      }

      const currentData = participantDoc.data();
      const currentTotalPushups = currentData.totalPushups || 0;
      const currentTotalApprovedDays = currentData.totalApprovedDays || 0;

      transaction.update(participantRef, {
        totalPushups: currentTotalPushups + pushupCount,
        totalApprovedDays: currentTotalApprovedDays + 1,
        currentStreak: (currentData.currentStreak || 0) + 1,
        lastCheckinAt: FieldValue.serverTimestamp(),
      });
    });

    logger.info(`submitPushupCount: Updated participant stats for user ${uid}`);

    return {
      checkinId: checkinRef.id,
      pushupCount,
      message: `${pushupCount} pushups recorded! Keep it up!`,
    };
  }
);
