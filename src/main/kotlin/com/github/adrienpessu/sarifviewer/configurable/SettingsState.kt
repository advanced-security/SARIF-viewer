package com.github.adrienpessu.sarifviewer.configurable

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*


@State(
        name = "SettingsState",
        storages = [Storage("sarif-viewer-plugin.xml")]
)
open class SettingsState : PersistentStateComponent<SettingsState.PluginState> {

    // this is how we're going to call the component from different classes
    companion object {
        val instance: SettingsState
            get() = ApplicationManager.getApplication().getService(SettingsState::class.java) ?: SettingsState()
    }

    // the component will always keep our state as a variable
    var pluginState: PluginState = PluginState()

    // just an obligatory override from PersistentStateComponent
    override fun getState(): PluginState {
        return pluginState
    }

    // after automatically loading our save state,  we will keep reference to it
    override fun loadState(paramState: PluginState) {
        pluginState = paramState
    }

    // the POKO class that always keeps our state
    class PluginState {
        var pat = "Insert your GitHub PAT here"
    }

}
