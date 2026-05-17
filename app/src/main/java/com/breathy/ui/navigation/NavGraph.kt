package com.breathy.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import com.breathy.BreathyApplication
import com.breathy.ui.auth.AuthScreen
import com.breathy.ui.auth.OnboardingScreen
import com.breathy.ui.coach.AICoachScreen
import com.breathy.ui.community.CommunityScreen
import com.breathy.ui.community.PostStoryScreen
import com.breathy.ui.community.PublicProfileScreen
import com.breathy.ui.community.StoryDetailScreen
import com.breathy.ui.events.AdminReviewScreen
import com.breathy.ui.events.EventChallengeScreen
import com.breathy.ui.events.EventCheckinScreen
import com.breathy.ui.events.EventsScreen
import com.breathy.ui.friends.ChatScreen
import com.breathy.ui.friends.FriendsScreen
import com.breathy.ui.home.HomeScreen
import com.breathy.ui.leaderboard.LeaderboardScreen
import com.breathy.ui.profile.AchievementsListScreen
import com.breathy.ui.profile.ProfileScreen
import com.breathy.ui.subscription.SubscriptionScreen
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.BgSurface
import com.breathy.ui.theme.TextDisabled
import com.breathy.ui.theme.TextPrimary
import com.google.firebase.auth.FirebaseAuth

// ═══════════════════════════════════════════════════════════════════════════════
// Route Constants — Type-safe navigation destinations
// ═══════════════════════════════════════════════════════════════════════════════

object BreathyRoutes {

    // ── Auth & Onboarding ───────────────────────────────────────────────────
    const val AUTH = "auth"
    const val ONBOARDING = "onboarding"

    // ── Main Screens (bottom bar destinations) ──────────────────────────────
    const val HOME = "home"
    const val COMMUNITY = "community"
    const val EVENTS = "events"
    const val LEADERBOARD = "leaderboard"
    const val PROFILE = "profile"

    // ── Detail / Secondary Screens ──────────────────────────────────────────
    const val STORY_DETAIL = "storyDetail/{storyId}"
    const val POST_STORY = "postStory"
    const val PUBLIC_PROFILE = "publicProfile/{userId}"
    const val FRIENDS = "friends"
    const val CHAT = "chat/{chatId}"
    const val EVENT_CHALLENGE = "eventChallenge/{eventId}"
    const val EVENT_CHECKIN = "eventCheckin/{eventId}"
    const val ADMIN_REVIEW = "adminReview"
    const val AI_COACH = "aiCoach"
    const val ACHIEVEMENTS = "achievements"
    const val SUBSCRIPTION = "subscription"

    // ── Helper functions for parameterized routes ───────────────────────────

    fun storyDetail(storyId: String): String = "storyDetail/$storyId"
    fun publicProfile(userId: String): String = "publicProfile/$userId"
    fun chat(chatId: String): String = "chat/$chatId"
    fun eventChallenge(eventId: String): String = "eventChallenge/$eventId"
    fun eventCheckin(eventId: String): String = "eventCheckin/$eventId"

    fun baseRoute(route: String?): String? {
        if (route == null) return null
        return route.substringBefore("/")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Bottom Navigation Configuration
// ═══════════════════════════════════════════════════════════════════════════════

private data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(BreathyRoutes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(BreathyRoutes.COMMUNITY, "Community", Icons.Filled.People, Icons.Outlined.People),
    BottomNavItem(BreathyRoutes.EVENTS, "Events", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    BottomNavItem(BreathyRoutes.LEADERBOARD, "Leaderboard", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
    BottomNavItem(BreathyRoutes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person)
)

private val noBottomBarRoutes = setOf(
    BreathyRoutes.AUTH,
    BreathyRoutes.ONBOARDING,
    BreathyRoutes.STORY_DETAIL,
    BreathyRoutes.POST_STORY,
    BreathyRoutes.CHAT,
    BreathyRoutes.EVENT_CHECKIN,
    BreathyRoutes.ADMIN_REVIEW,
    BreathyRoutes.AI_COACH,
    BreathyRoutes.SUBSCRIPTION,
    BreathyRoutes.ACHIEVEMENTS,
    BreathyRoutes.PUBLIC_PROFILE,
    BreathyRoutes.FRIENDS,
    BreathyRoutes.EVENT_CHALLENGE
)

// ═══════════════════════════════════════════════════════════════════════════════
// Animation Specs
// ═══════════════════════════════════════════════════════════════════════════════

private const val ANIM_DURATION_MS = 250

private val enterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    fadeIn(animationSpec = tween(ANIM_DURATION_MS)) + slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(ANIM_DURATION_MS)
    )
}

private val exitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    fadeOut(animationSpec = tween(ANIM_DURATION_MS))
}

private val popEnterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    fadeIn(animationSpec = tween(ANIM_DURATION_MS))
}

private val popExitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    fadeOut(animationSpec = tween(ANIM_DURATION_MS)) + slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(ANIM_DURATION_MS)
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// Main Navigation Host
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun BreathyNavHost(
    deepLinkRoute: String?,
    onDeepLinkConsumed: () -> Unit,
    onGoogleSignInRequest: () -> Unit = {},
    googleIdToken: String? = null,
    onGoogleTokenConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current
    val app = context.applicationContext as BreathyApplication

    // ── Deep link routing ───────────────────────────────────────────────────
    LaunchedEffect(deepLinkRoute) {
        if (deepLinkRoute != null) {
            try {
                navController.navigate(deepLinkRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            } catch (e: IllegalArgumentException) {
                navController.navigate(BreathyRoutes.HOME) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            onDeepLinkConsumed()
        }
    }

    // ── Bottom bar visibility ───────────────────────────────────────────────
    val showBottomBar = currentDestination?.route != null &&
            currentDestination?.route !in noBottomBarRoutes

    // ── Navigation helpers ──────────────────────────────────────────────────
    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToWithAd(route: String) {
        val activity = context as? Activity
        if (activity != null) {
            app.appModule.adManager.showInterstitialAd(activity) {
                navigateTo(route)
            }
        } else {
            navigateTo(route)
        }
    }

    fun navigateBack() {
        navController.popBackStack()
    }

    fun signOutAndNavigateToAuth() {
        FirebaseAuth.getInstance().signOut()
        navController.navigate(BreathyRoutes.AUTH) {
            popUpTo(0) { inclusive = true }
        }
    }

    // ── Scaffold with conditional bottom bar ────────────────────────────────
    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                BreathyBottomBar(
                    currentDestination = currentDestination,
                    onNavigate = { route -> navigateTo(route) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BreathyRoutes.AUTH,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { popEnterTransition() },
            popExitTransition = { popExitTransition() }
        ) {
            // ── Auth ────────────────────────────────────────────────────
            composable(BreathyRoutes.AUTH) {
                AuthScreen(
                    onNavigateToHome = {
                        // Clear the entire back stack so the user can't go back to Auth
                        navController.navigate(BreathyRoutes.HOME) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToOnboarding = { navigateTo(BreathyRoutes.ONBOARDING) },
                    onGoogleSignInRequest = onGoogleSignInRequest,
                    googleIdToken = googleIdToken,
                    onGoogleTokenConsumed = onGoogleTokenConsumed
                )
            }

            // ── Onboarding ──────────────────────────────────────────────
            composable(BreathyRoutes.ONBOARDING) {
                OnboardingScreen(
                    onNavigateToHome = {
                        // Clear the entire back stack (Auth + Onboarding) so the
                        // user can't go back to those screens after completing setup.
                        navController.navigate(BreathyRoutes.HOME) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // ── Home ────────────────────────────────────────────────────
            composable(BreathyRoutes.HOME) {
                HomeScreen(
                    onNavigateToProfile = { navigateTo(BreathyRoutes.PROFILE) },
                    onNavigateToAICoach = { navigateTo(BreathyRoutes.AI_COACH) }
                )
            }

            // ── Community ───────────────────────────────────────────────
            composable(BreathyRoutes.COMMUNITY) {
                CommunityScreen(
                    onNavigateToStoryDetail = { storyId ->
                        navigateToWithAd(BreathyRoutes.storyDetail(storyId))
                    },
                    onNavigateToPostStory = { navigateTo(BreathyRoutes.POST_STORY) },
                    onNavigateToProfile = { userId ->
                        navigateToWithAd(BreathyRoutes.publicProfile(userId))
                    },
                    onNavigateToFriends = { navController.navigate(BreathyRoutes.FRIENDS) }
                )
            }

            // ── Story Detail ────────────────────────────────────────────
            composable(
                route = BreathyRoutes.STORY_DETAIL,
                arguments = listOf(
                    navArgument("storyId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val storyId = backStackEntry.arguments?.getString("storyId")
                    ?: return@composable
                StoryDetailScreen(
                    storyId = storyId,
                    onNavigateBack = { navigateBack() },
                    onNavigateToProfile = { userId ->
                        navigateToWithAd(BreathyRoutes.publicProfile(userId))
                    }
                )
            }

            // ── Post Story ──────────────────────────────────────────────
            composable(BreathyRoutes.POST_STORY) {
                PostStoryScreen(
                    onNavigateBack = { navigateBack() },
                    onStoryPosted = {
                        // Pre-load interstitial ad, then navigate back to community
                        app.appModule.adManager.loadInterstitialAd()
                        navigateBack()
                    }
                )
            }

            // ── Public Profile ──────────────────────────────────────────
            composable(
                route = BreathyRoutes.PUBLIC_PROFILE,
                arguments = listOf(
                    navArgument("userId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId")
                    ?: return@composable
                PublicProfileScreen(
                    userId = userId,
                    onNavigateBack = { navigateBack() },
                    onNavigateToStoryDetail = { sId ->
                        navController.navigate(BreathyRoutes.storyDetail(sId))
                    },
                    onNavigateToChat = { chatId ->
                        navigateToWithAd(BreathyRoutes.chat(chatId))
                    }
                )
            }

            // ── Friends ─────────────────────────────────────────────────
            composable(BreathyRoutes.FRIENDS) {
                FriendsScreen(
                    onNavigateBack = { navigateBack() },
                    onNavigateToChat = { chatId ->
                        navigateToWithAd(BreathyRoutes.chat(chatId))
                    }
                )
            }

            // ── Chat ────────────────────────────────────────────────────
            composable(
                route = BreathyRoutes.CHAT,
                arguments = listOf(
                    navArgument("chatId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId")
                    ?: return@composable
                ChatScreen(
                    chatId = chatId,
                    onNavigateBack = { navigateBack() }
                )
            }

            // ── Leaderboard ─────────────────────────────────────────────
            composable(BreathyRoutes.LEADERBOARD) {
                LeaderboardScreen(
                    onNavigateBack = { navigateBack() },
                    onNavigateToProfile = { userId ->
                        navigateToWithAd(BreathyRoutes.publicProfile(userId))
                    }
                )
            }

            // ── Events ──────────────────────────────────────────────────
            composable(BreathyRoutes.EVENTS) {
                EventsScreen(
                    onNavigateBack = { navigateBack() },
                    onNavigateToEventDetail = { eventId ->
                        navigateToWithAd(BreathyRoutes.eventChallenge(eventId))
                    }
                )
            }

            // ── Event Challenge Detail ──────────────────────────────────
            composable(
                route = BreathyRoutes.EVENT_CHALLENGE,
                arguments = listOf(
                    navArgument("eventId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getString("eventId")
                    ?: return@composable
                EventChallengeScreen(
                    eventId = eventId,
                    onNavigateBack = { navigateBack() }
                )
            }

            // ── Event Check-in ──────────────────────────────────────────
            composable(
                route = BreathyRoutes.EVENT_CHECKIN,
                arguments = listOf(
                    navArgument("eventId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getString("eventId")
                    ?: return@composable
                EventCheckinScreen(
                    eventId = eventId,
                    onNavigateBack = { navigateBack() }
                )
            }

            // ── Admin Review ────────────────────────────────────────────
            composable(BreathyRoutes.ADMIN_REVIEW) {
                AdminReviewScreen(
                    onNavigateBack = { navigateBack() }
                )
            }

            // ── AI Coach ────────────────────────────────────────────────
            composable(BreathyRoutes.AI_COACH) {
                AICoachScreen(
                    onBack = { navigateBack() }
                )
            }

            // ── Profile ─────────────────────────────────────────────────
            composable(BreathyRoutes.PROFILE) {
                ProfileScreen(
                    onNavigateToAchievements = { navigateTo(BreathyRoutes.ACHIEVEMENTS) },
                    onNavigateToSubscription = { navigateTo(BreathyRoutes.SUBSCRIPTION) },
                    onNavigateToAICoach = { navigateTo(BreathyRoutes.AI_COACH) },
                    onNavigateToFriends = { navController.navigate(BreathyRoutes.FRIENDS) },
                    onSignOut = { signOutAndNavigateToAuth() }
                )
            }

            // ── Achievements ────────────────────────────────────────────
            composable(BreathyRoutes.ACHIEVEMENTS) {
                AchievementsListScreen(
                    onBack = { navigateBack() }
                )
            }

            // ── Subscription ────────────────────────────────────────────
            composable(BreathyRoutes.SUBSCRIPTION) {
                SubscriptionScreen(
                    onBack = { navigateBack() }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Bottom Navigation Bar
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BreathyBottomBar(
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = BgSurface,
        contentColor = TextPrimary
    ) {
        bottomNavItems.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any {
                it.route == item.route
            } == true

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = if (isSelected) AccentPrimary else TextDisabled
                    )
                },
                label = {
                    if (isSelected) {
                        Text(
                            text = item.label,
                            fontSize = 10.sp,
                            color = AccentPrimary
                        )
                    }
                },
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = AccentPrimary.copy(alpha = 0.12f),
                    selectedIconColor = AccentPrimary,
                    unselectedIconColor = TextDisabled
                )
            )
        }
    }
}
