package com.iris.assistant.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.iris.assistant.ui.onboarding.OnboardingWakeWordScreen
import com.iris.assistant.ui.onboarding.OnboardingWelcomeScreen
import com.iris.assistant.ui.settings.AppearanceSettingsScreen
import com.iris.assistant.ui.settings.AutonomySettingsScreen
import com.iris.assistant.ui.settings.BackgroundSettingsScreen
import com.iris.assistant.ui.settings.DataSettingsScreen
import com.iris.assistant.ui.settings.LocalModelScreen
import com.iris.assistant.ui.settings.ModelSettingsScreen
import com.iris.assistant.ui.settings.PermissionScreen
import com.iris.assistant.ui.settings.SettingsScreen
import com.iris.assistant.ui.settings.SystemSettingsScreen
import com.iris.assistant.ui.settings.VoiceSettingsScreen
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

fun enterAnim(): EnterTransition = slideInHorizontally(
    initialOffsetX = { it },
    animationSpec = tween(durationMillis = 340)
) + fadeIn(animationSpec = tween(durationMillis = 340))

fun exitAnim(): ExitTransition = slideOutHorizontally(
    targetOffsetX = { -it / 4 },
    animationSpec = tween(durationMillis = 340)
) + fadeOut(animationSpec = tween(durationMillis = 340))

fun popEnterAnim(): EnterTransition = slideInHorizontally(
    initialOffsetX = { -it / 4 },
    animationSpec = tween(durationMillis = 340)
) + fadeIn(animationSpec = tween(durationMillis = 340))

fun popExitAnim(): ExitTransition = slideOutHorizontally(
    targetOffsetX = { it },
    animationSpec = tween(durationMillis = 340)
) + fadeOut(animationSpec = tween(durationMillis = 340))

private fun mainEnter(): EnterTransition = enterAnim()
private fun mainExit(): ExitTransition = exitAnim()
private fun mainPopEnter(): EnterTransition = popEnterAnim()
private fun mainPopExit(): ExitTransition = popExitAnim()

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val repository: ConversationRepository
) : ViewModel() {
    val conversations: StateFlow<List<Conversation>> = repository.getConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
        }
    }
}

@Composable
fun IrisNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavRoute.OnboardingWelcome.route,
) {
    val drawerViewModel: DrawerViewModel = hiltViewModel()
    val conversations by drawerViewModel.conversations.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    val showDrawer = currentRoute?.startsWith("onboarding") == false
    val drawerGestures = currentRoute == NavRoute.Home.route

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = showDrawer && drawerGestures,
            scrimColor = DrawerDefaults.scrimColor,
            drawerContent = {
                if (showDrawer) {
                    IrisDrawerSheet(
                        drawerState = drawerState,
                        conversations = conversations,
                        currentRoute = currentRoute,
                        onHomeClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(NavRoute.Home.route) {
                                popUpTo(NavRoute.Home.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onNewChatClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(NavRoute.Chat.NEW) {
                                launchSingleTop = false
                            }
                        },
                        onConversationClick = { conv ->
                            scope.launch { drawerState.close() }
                            navController.navigate(NavRoute.Chat.withId(conv.id)) {
                                launchSingleTop = false
                            }
                        },
                        onDeleteConversation = { conv ->
                            drawerViewModel.deleteConversation(conv.id)
                        },
                    )
                }
            },
        ) {
            NavContent(
                navController = navController,
                startDestination = startDestination,
                drawerState = drawerState,
            )
        }
    }
}

@Composable
private fun NavContent(
    navController: NavHostController,
    startDestination: String,
    drawerState: DrawerState,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { enterAnim() },
        exitTransition = { exitAnim() },
        popEnterTransition = { popEnterAnim() },
        popExitTransition = { popExitAnim() },
        modifier = Modifier.fillMaxSize()
    ) {
        composable(NavRoute.OnboardingWelcome.route) { OnboardingWelcomeScreen(navController) }
        composable(NavRoute.OnboardingMic.route) { OnboardingMicScreen(navController) }
        composable(NavRoute.OnboardingWakeWord.route) { OnboardingWakeWordScreen(navController) }
        composable(NavRoute.OnboardingDemo.route) { OnboardingDemoScreen(navController) }
        composable(NavRoute.OnboardingAssistant.route) { OnboardingAssistantScreen(navController) }
        composable(NavRoute.OnboardingBattery.route) { OnboardingBatteryScreen(navController) }

        composable(
            route              = NavRoute.Home.route,
            enterTransition    = { mainEnter() },
            exitTransition     = { mainExit() },
            popEnterTransition = { mainPopEnter() },
            popExitTransition  = { mainPopExit() },
        ) { HomeScreen(navController, drawerState) }
        composable(
            route              = NavRoute.Settings.route,
            enterTransition    = { mainEnter() },
            exitTransition     = { mainExit() },
            popEnterTransition = { mainPopEnter() },
            popExitTransition  = { mainPopExit() },
        ) { SettingsScreen(navController) }
        composable(
            route              = NavRoute.SettingsModel.route,
            enterTransition    = { mainEnter() },
            exitTransition     = { mainExit() },
            popEnterTransition = { mainPopEnter() },
            popExitTransition  = { mainPopExit() },
        ) { ModelSettingsScreen(navController) }
        composable(
            route              = NavRoute.SettingsAppearance.route,
            enterTransition    = { mainEnter() },
            exitTransition     = { mainExit() },
            popEnterTransition = { mainPopEnter() },
            popExitTransition  = { mainPopExit() },
        ) { AppearanceSettingsScreen(navController) }
        composable(
            route              = NavRoute.SettingsBackground.route,
            enterTransition    = { mainEnter() },
            exitTransition     = { mainExit() },
            popEnterTransition = { mainPopEnter() },
            popExitTransition  = { mainPopExit() },
        ) { BackgroundSettingsScreen(navController) }
        composable(
            route              = NavRoute.SettingsAutonomy.route,
            enterTransition    = { mainEnter() },
            exitTransition     = { mainExit() },
            popEnterTransition = { mainPopEnter() },
            popExitTransition  = { mainPopExit() },
        ) { AutonomySettingsScreen(navController) }
        composable(
            route              = NavRoute.SettingsSystem.route,
            enterTransition    = { mainEnter() },
            exitTransition     = { mainExit() },
            popEnterTransition = { mainPopEnter() },
            popExitTransition  = { mainPopExit() },
        ) { SystemSettingsScreen(navController) }
        composable(
            route              = NavRoute.SettingsData.route,
            enterTransition    = { mainEnter() },
            exitTransition     = { mainExit() },
            popEnterTransition = { mainPopEnter() },
            popExitTransition  = { mainPopExit() },
        ) { DataSettingsScreen(navController) }
        composable(
            route              = NavRoute.LocalModels.route,
            enterTransition    = { mainEnter() },
            exitTransition     = { mainExit() },
            popEnterTransition = { mainPopEnter() },
            popExitTransition  = { mainPopExit() },
        ) { LocalModelScreen(navController) }
        composable(
            route              = NavRoute.PermissionManager.route,
            enterTransition    = { mainEnter() },
            exitTransition     = { mainExit() },
            popEnterTransition = { mainPopEnter() },
            popExitTransition  = { mainPopExit() },
        ) { PermissionScreen(navController) }
        composable(
            route              = NavRoute.VoiceSettings.route,
            enterTransition    = { mainEnter() },
            exitTransition     = { mainExit() },
            popEnterTransition = { mainPopEnter() },
            popExitTransition  = { mainPopExit() },
        ) { VoiceSettingsScreen(navController) }

        composable(
            route = NavRoute.Chat.route,
            arguments = listOf(navArgument(NavRoute.Chat.ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getLong(NavRoute.Chat.ARG) ?: 0L
            ChatScreen(navController, drawerState, conversationId)
        }
    }
}

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
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        modifier = Modifier
            .fillMaxHeight()
            .width(310.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text(
                    text = "Iris",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            DrawerNavigationItem(
                icon = PhIcons.Regular.House,
                label = "Ana Sayfa",
                isSelected = currentRoute == NavRoute.Home.route,
                onClick = onHomeClick
            )

            DrawerNavigationItem(
                icon = PhIcons.Regular.Plus,
                label = "Yeni Sohbet",
                isSelected = currentRoute == NavRoute.Chat.NEW || (currentRoute?.startsWith("chat") == true && conversations.none { NavRoute.Chat.withId(it.id) == currentRoute }),
                onClick = onNewChatClick
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    val isSelected = currentRoute == NavRoute.Chat.withId(conversation.id)
                    DrawerConversationItem(
                        conversation = conversation,
                        isSelected = isSelected,
                        onClick = { onConversationClick(conversation) },
                        onDelete = { onDeleteConversation(conversation) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerNavigationItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isSelected) {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                Color.Transparent
            )
        )
    } else {
        Brush.horizontalGradient(colors = listOf(Color.Transparent, Color.Transparent))
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .background(background)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DrawerConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val background = if (isSelected) {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                Color.Transparent
            )
        )
    } else {
        Brush.horizontalGradient(colors = listOf(Color.Transparent, Color.Transparent))
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .background(background)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = PhIcons.Regular.ChatCircle,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = PhIcons.Regular.TrashSimple,
                    contentDescription = "Sil",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
