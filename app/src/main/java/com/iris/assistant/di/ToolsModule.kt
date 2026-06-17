package com.iris.assistant.di

import com.iris.assistant.data.tools.communication.MakeCallTool
import com.iris.assistant.data.tools.communication.PostNotificationTool
import com.iris.assistant.data.tools.communication.SendSmsTool
import com.iris.assistant.data.tools.info.GetNewsTool
import com.iris.assistant.data.tools.info.GetWeatherTool
import com.iris.assistant.data.tools.info.WebSearchTool
import com.iris.assistant.data.tools.productivity.AddCalendarEventTool
import com.iris.assistant.data.tools.productivity.AddReminderTool
import com.iris.assistant.data.tools.productivity.SetAlarmTool
import com.iris.assistant.data.tools.screen.ClickTool
import com.iris.assistant.data.tools.screen.NavigateTool
import com.iris.assistant.data.tools.screen.ReadScreenTool
import com.iris.assistant.data.tools.screen.ScrollTool
import com.iris.assistant.data.tools.screen.TypeTool
import com.iris.assistant.data.tools.system.GetBatteryStatusTool
import com.iris.assistant.data.tools.system.OpenAppTool
import com.iris.assistant.data.tools.system.SetBrightnessTool
import com.iris.assistant.data.tools.system.SetVolumeTool
import com.iris.assistant.data.tools.system.ToggleBluetoothTool
import com.iris.assistant.data.tools.system.ToggleFlashlightTool
import com.iris.assistant.data.tools.system.ToggleWifiTool
import com.iris.assistant.domain.tools.CalculateTool
import com.iris.assistant.domain.tools.GetCurrentTimeTool
import com.iris.assistant.domain.tools.JarvisTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolsModule {

    // --- Built-in ---

    @Binds @IntoSet
    abstract fun bindGetCurrentTimeTool(impl: GetCurrentTimeTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindCalculateTool(impl: CalculateTool): JarvisTool

    // --- System ---

    @Binds @IntoSet
    abstract fun bindOpenAppTool(impl: OpenAppTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindSetVolumeTool(impl: SetVolumeTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindSetBrightnessTool(impl: SetBrightnessTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindToggleWifiTool(impl: ToggleWifiTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindToggleBluetoothTool(impl: ToggleBluetoothTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindToggleFlashlightTool(impl: ToggleFlashlightTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindGetBatteryStatusTool(impl: GetBatteryStatusTool): JarvisTool

    // --- Info ---

    @Binds @IntoSet
    abstract fun bindGetWeatherTool(impl: GetWeatherTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindWebSearchTool(impl: WebSearchTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindGetNewsTool(impl: GetNewsTool): JarvisTool

    // --- Communication ---

    @Binds @IntoSet
    abstract fun bindMakeCallTool(impl: MakeCallTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindSendSmsTool(impl: SendSmsTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindPostNotificationTool(impl: PostNotificationTool): JarvisTool

    // --- Productivity ---

    @Binds @IntoSet
    abstract fun bindSetAlarmTool(impl: SetAlarmTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindAddReminderTool(impl: AddReminderTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindAddCalendarEventTool(impl: AddCalendarEventTool): JarvisTool

    // --- Screen Intelligence ---

    @Binds @IntoSet
    abstract fun bindReadScreenTool(impl: ReadScreenTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindClickTool(impl: ClickTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindTypeTool(impl: TypeTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindScrollTool(impl: ScrollTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindNavigateTool(impl: NavigateTool): JarvisTool
}
