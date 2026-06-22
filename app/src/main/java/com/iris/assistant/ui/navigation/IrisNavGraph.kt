package com.iris.assistant.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iris.assistant.domain.model.Conversation
import com.iris.assistant.domain.repository.ConversationRepository
import com.iris.assistant.ui.chat.ChatScreen
import com.iris.assistant.ui.home.HomeScreen
import com.iris.assistant.ui.onboarding.OnboardingAssistantScreen
import com.iris.assistant.ui.onboarding.OnboardingBatteryScreen
import com.iris.assistant.ui.onboarding.OnboardingDemoScreen
import com.iris.assistant.ui.onboarding.OnboardingMicScreen
import com.iris.assistant.ui.onboarding.OnboardingViewModel
import com.iris.assistant.ui.onboarding.OnboardingWakeWordScreen
import com.iris.assistant.ui.onboarding.OnboardingWelcomeScreen
import com.iris.assistant.ui.settings.AppearanceSettingsScreen
import com.iris.assistant.ui.settings.AutonomySettingsScreen
import com.iris.assistant.ui.settings.BackgroundSettingsScreen
import com.iris.assistant.ui.settings.DataSettingsScreen
import com.iris.assistant.ui.settings.LocalModelScreen
import com.iris.assistant.ui.settings.ModelSettingsScreen
import com.iris.assistant.ui.settings.PermissionScreen
import com.iris.assistant.ui.settings.PowerModeScreen
import com.iris.assistant.ui.settings.SettingsScreen
import com.iris.assistant.ui.settings.SystemSettingsScreen
import com.iris.assistant.ui.settings.VoiceSettingsScreen
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.util.Constants
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ChatCircle
import com.phosphor.icons.regular.House
import com.phosphor.icons.regular.Plus
import com.phosphor.icons.regular.TrashSimple
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Transition helpers
// ---------------------------------------------------------------------------

private fun onboardingEnter(): EnterTransition =
    slideInHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { it / 4 } +
        fadeIn(tween(Constants.NAV_ANIM_DURATION_MS))

private fun onboardingExit(): ExitTransition =
    slideOutHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { -it / 4 } +
        fadeOut(tween(Constants.NAV_ANIM_DURATION_MS))

private fun onboardingPopEnter(): EnterTransition =
    slideInHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { -it / 4 } +
        fadeIn(tween(Constants.NAV_ANIM_DURATION_MS))

private fun onboardingPopExit(): ExitTransition =
    slideOutHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { it / 4 } +
        fadeOut(tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS)
    ) { fullWidth -> fullWidth / Constants.NAV_SLIDE_ENTER_DIVISOR } +
    scaleIn(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS),
        initialScale  = Constants.NAV_SCALE_ENTER_FROM
    ) + fadeIn(animationSpec = tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS)
    ) { fullWidth -> -fullWidth / Constants.NAV_SLIDE_EXIT_DIVISOR } +
    scaleOut(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS),
        targetScale   = Constants.NAV_SCALE_EXIT_TO
    ) + fadeOut(animationSpec = tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainPopEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS)
    ) { fullWidth -> -fullWidth / Constants.NAV_SLIDE_EXIT_DIVISOR } +
    scaleIn(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS),
        initialScale  = Constants.NAV_SCALE_EXIT_TO
    ) + fadeIn(animationSpec = tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainPopExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS)
    ) { fullWidth -> fullWidth / Constants.NAV_SLIDE_ENTER_DIVISOR } +
    scaleOut(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS),
        targetScale   = Constants.NAV_SCALE_ENTER_FROM
    ) + fadeOut(animationSpec = tween(Constants.NAV_ANIM_DURATION_MS))

private fun chatEnter(): EnterTransition =
    slideInHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { it } +
        fadeIn(tween(Constants.NAV_ANIM_DURATION_MS))

private fun chatExit(): ExitTransition =
    slideOutHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { it } +
        fadeOut(tween(Constants.NAV_ANIM_DURATION_MS))

private fun chatPopEnter(): EnterTransition =
    slideInHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { -it } +
        fadeIn(tween(Constants.NAV_ANIM_DURATION_MS))

// ---------------------------------------------------------------------------
// DrawerViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val repo: ConversationRepository,
) : ViewModel() {

    val conversations: StateFlow<List<Conversation>> = repo.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteConversation(id: Long) {
        viewModelScope.launch { repo.deleteConversation(id) }
    }
}

// ---------------------------------------------------------------------------
// IrisNavGraph — root composable, owns drawer state
// ---------------------------------------------------------------------------

@Composable
fun IrisNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavRoute.OnboardingWelcome.route,
) {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val drawerViewModel: DrawerViewModel = hiltViewModel()
    val conversations by drawerViewModel.conversations.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    val showDrawer     = currentRoute?.startsWith("onboarding") == false
    val drawerGestures = currentRoute != NavRoute.Home.route &&
                         currentRoute?.startsWith("chat") != true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (showDrawer) {
            ModalNavigationDrawer(
                drawerState     = drawerState,
                gesturesEnabled = drawerGestures,
                scrimColor      = DrawerDefaults.scrimColor,
                drawerContent   = {
                    IrisDrawerSheet(
                        drawerState          = drawerState,
                        conversations        = conversations,
                        currentRoute         = currentRoute,
                        onHomeClick          = {
                            scope.launch { drawerState.close() }
                            navController.navigate(NavRoute.Home.route) {
                                popUpTo(NavRoute.Home.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onNewChatClick       = {
                            scope.launch { drawerState.close() }
                            navController.navigate(NavRoute.Chat.NEW) {
                                launchSingleTop = false
                            }
                        },
                        onConversationClick  = { conv ->
                            scope.launch { drawerState.close() }
                            navController.navigate(NavRoute.Chat.withId(conv.id)) {
                                launchSingleTop = false
                            }
                        },
                        onDeleteConversation = { conv ->
                            drawerViewModel.deleteConversation(conv.id)
                        },
                    )
                },
            ) {
                NavContent(
                    navController       = navController,
                    startDestination    = startDestination,
                    onboardingViewModel = onboardingViewModel,
                    drawerState         = drawerState,
                )
            }
        } else {
            NavContent(
                navController       = navController,
                startDestination    = startDestination,
                onboardingViewModel = onboardingViewModel,
                drawerState         = drawerState,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// NavContent
// ---------------------------------------------------------------------------

@Composable
private fun NavContent(
    navController: NavHostController,
    startDestination: String,
    onboardingViewModel: OnboardingViewModel,
    drawerState: DrawerState,
) {
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    NavHost(
        navController      = navController,
        startDestination   = startDestination,
        enterTransition    = { mainEnter() },
        exitTransition     = { mainExit() },
        popEnterTransition = { mainPopEnter() },
        popExitTransition  = { mainPopExit() },
    ) {
        // --- Onboarding ---
        composable(
            route           = NavRoute.OnboardingWelcome.route,
            enterTransition = { onboardingEnter() },
            exitTransition  = { onboardingExit() },
        ) {
            val userName by onboardingViewModel.userName.collectAsStateWithLifecycle()
            OnboardingWelcomeScreen(
                userName         = userName,
                onUserNameChange = onboardingViewModel::setUserName,
                onNext           = { navController.navigate(NavRoute.OnboardingMic.route) },
            )
        }
        composable(
            route              = NavRoute.OnboardingMic.route,
            enterTransition    = { onboardingEnter() },
            exitTransition     = { onboardingExit() },
            popEnterTransition = { onboardingPopEnter() },
            popExitTransition  = { onboardingPopExit() },
        ) {
            OnboardingMicScreen(
                onNext = { navController.navigate(NavRoute.OnboardingWakeWord.route) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route              = NavRoute.OnboardingWakeWord.route,
            enterTransition    = { onboardingEnter() },
            exitTransition     = { onboardingExit() },
            popEnterTransition = { onboardingPopEnter() },
            popExitTransition  = { onboardingPopExit() },
        ) {
            OnboardingWakeWordScreen(
                onNext = { navController.navigate(NavRoute.OnboardingDemo.route) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route              = NavRoute.OnboardingDemo.route,
            enterTransition    = { onboardingEnter() },
            exitTransition     = { onboardingExit() },
            popEnterTransition = { onboardingPopEnter() },
            popExitTransition  = { onboardingPopExit() },
        ) {
            OnboardingDemoScreen(
                onNext = { navController.navigate(NavRoute.OnboardingAssistant.route) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route              = NavRoute.OnboardingAssistant.route,
            enterTransition    = { onboardingEnter() },
            exitTransition     = { onboardingExit() },
            popEnterTransition = { onboardingPopEnter() },
            popExitTransition  = { onboardingPopExit() },
        ) {
            OnboardingAssistantScreen(
                onNext = { navController.navigate(NavRoute.OnboardingBattery.route) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route              = NavRoute.OnboardingBattery.route,
            enterTransition    = { onboardingEnter() },
            exitTransition     = { mainExit() },
            popEnterTransition = { onboardingPopEnter() },
            popExitTransition  = { onboardingPopExit() },
        ) {
            OnboardingBatteryScreen(
                onFinish  = {
                    navController.navigate(NavRoute.Home.route) {
                        popUpTo(NavRoute.OnboardingWelcome.route) { inclusive = true }
                    }
                },
                onBack    = { navController.popBackStack() },
                viewModel = onboardingViewModel,
            )
        }

        // --- Main ---
        composable(route = NavRoute.Home.route) {
            HomeScreen(
                onOpenSettings = { navController.navigate(NavRoute.Settings.route) },
                onOpenDrawer   = openDrawer,
            )
        }
        composable(route = NavRoute.Settings.route) {
            SettingsScreen(
                onBack           = { navController.popBackStack() },
                onOpenVoice      = { navController.navigate(NavRoute.VoiceSettings.route) },
                onOpenModel      = { navController.navigate(NavRoute.SettingsModel.route) },
                onOpenAppearance = { navController.navigate(NavRoute.SettingsAppearance.route) },
                onOpenBackground = { navController.navigate(NavRoute.SettingsBackground.route) },
                onOpenAutonomy   = { navController.navigate(NavRoute.SettingsAutonomy.route) },
                onOpenSystem     = { navController.navigate(NavRoute.SettingsSystem.route) },
                onOpenData       = { navController.navigate(NavRoute.SettingsData.route) },
                onOpenPowerMode  = { navController.navigate(NavRoute.SettingsPowerMode.route) },
            )
        }
        composable(route = NavRoute.SettingsModel.route) {
            ModelSettingsScreen(
                onBack            = { navController.popBackStack() },
                onOpenLocalModels = { navController.navigate(NavRoute.LocalModels.route) },
            )
        }
        composable(route = NavRoute.SettingsAppearance.route) {
            AppearanceSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(route = NavRoute.SettingsBackground.route) {
            BackgroundSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(route = NavRoute.SettingsAutonomy.route) {
            AutonomySettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(route = NavRoute.SettingsSystem.route) {
            SystemSettingsScreen(
                onBack                  = { navController.popBackStack() },
                onOpenVoiceSettings     = { navController.navigate(NavRoute.VoiceSettings.route) },
                onOpenPermissionManager = { navController.navigate(NavRoute.PermissionManager.route) },
            )
        }
        composable(route = NavRoute.SettingsData.route) {
            DataSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(route = NavRoute.LocalModels.route) {
            LocalModelScreen(onBack = { navController.popBackStack() })
        }
        composable(route = NavRoute.PermissionManager.route) {
            PermissionScreen(onBack = { navController.popBackStack() })
        }
        composable(route = NavRoute.VoiceSettings.route) {
            VoiceSettingsScreen(onBack = { navController.popBackStack() })
        }
        // Phase 4 — Power Mode
        composable(route = NavRoute.SettingsPowerMode.route) {
            PowerModeScreen(onBack = { navController.popBackStack() })
        }

        // --- Chat ---
        composable(
            route      = NavRoute.Chat.route,
            arguments  = listOf(navArgument(NavRoute.Chat.ARG) { type = NavType.LongType }),
            enterTransition    = { chatEnter() },
            exitTransition     = { chatExit() },
            popEnterTransition = { chatPopEnter() },
            popExitTransition  = { chatExit() },
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getLong(NavRoute.Chat.ARG) ?: 0L
            ChatScreen(
                conversationId = conversationId,
                onBack         = { navController.popBackStack() },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// IrisDrawerSheet
// ---------------------------------------------------------------------------

@Composable
private fun IrisDrawerSheet(
    drawerState: DrawerState,
    conversations: List<Conversation>,
    currentRoute: String?,
    onHomeClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onConversationClick: (Conversation) -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
) {
    val primary = IrisTheme.colors.primary

    ModalDrawerSheet(
        drawerState          = drawerState,
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerShape          = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        modifier             = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.82f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier         = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text       = "Iris",
                    style      = MaterialTheme.typography.titleLarge,
                    color      = ColorTextSecondary,
                    fontWeight = FontWeight.Medium,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))

            DrawerNavItem(
                icon     = PhIcons.Regular.House,
                label    = "Ana Ekran",
                selected = currentRoute == NavRoute.Home.route,
                onClick  = onHomeClick,
            )
            DrawerNavItem(
                icon     = PhIcons.Regular.ChatCircle,
                label    = "Sohbet",
                selected = currentRoute?.startsWith("chat/") == true,
                onClick  = onNewChatClick,
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))

            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = "Sohbetler",
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick  = onNewChatClick,
                    shape    = CircleShape,
                    color    = primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            PhIcons.Regular.Plus,
                            contentDescription = "Yeni Sohbet",
                            tint     = primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (conversations.isEmpty()) {
                    item {
                        Text(
                            text     = "Henüz sohbet yok",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                } else {
                    items(conversations, key = { it.id }) { conv ->
                        DrawerConversationItem(
                            conversation = conv,
                            onClick      = { onConversationClick(conv) },
                            onDelete     = { onDeleteConversation(conv) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// DrawerNavItem
// ---------------------------------------------------------------------------

@Composable
private fun DrawerNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = IrisTheme.colors.primary
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(14.dp),
        color    = if (selected) primary.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text  = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// DrawerConversationItem
// ---------------------------------------------------------------------------

@Composable
private fun DrawerConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(12.dp),
        color    = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text     = conversation.title,
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick  = onDelete,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector        = PhIcons.Regular.TrashSimple,
                    contentDescription = "Sil",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(16.dp),
                )
            }
        }
    }
}