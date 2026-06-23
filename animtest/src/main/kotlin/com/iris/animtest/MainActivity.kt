package com.iris.animtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.iris.animtest.screens.ScreenA
import com.iris.animtest.screens.ScreenB
import com.iris.animtest.screens.ScreenC
import com.iris.animtest.screens.ScreenD
import com.iris.animtest.transitions.TransitionPreset

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AnimTestApp()
                }
            }
        }
    }
}

@Composable
private fun AnimTestApp() {
    var selectedPreset by remember { mutableStateOf(TransitionPreset.TaskManager) }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = selectedPreset.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth(),
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f),
            ) {
                TransitionPreset.entries.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.label, fontSize = 14.sp) },
                        onClick = {
                            selectedPreset = preset
                            expanded = false
                        },
                    )
                }
            }
        }

        key(selectedPreset) {
            val navController = rememberNavController()
            val t = selectedPreset.get
            NavHost(
                navController = navController,
                startDestination = "screen_a",
                enterTransition = { t.enter },
                exitTransition = { t.exit },
                popEnterTransition = { t.popEnter },
                popExitTransition = { t.popExit },
            ) {
                composable("screen_a") { ScreenA(navController) }
                composable("screen_b") { ScreenB(navController) }
                composable("screen_c") { ScreenC(navController) }
                composable("screen_d") { ScreenD(navController) }
            }
        }
    }
}
