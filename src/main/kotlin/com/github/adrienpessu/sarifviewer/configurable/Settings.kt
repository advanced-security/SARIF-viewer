package com.github.adrienpessu.sarifviewer.configurable

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.util.messages.Topic
import javax.swing.JComponent


class Settings : Configurable, Configurable.NoScroll, Disposable {
    private var mySettingsComponent: SettingComponent? = null

    interface SettingsSavedListener {
        fun settingsSaved()
    }

    override fun getDisplayName(): String {
        return "SARIF Viewer Settings"
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return mySettingsComponent!!.getPreferredFocusedComponent()
    }

    override fun createComponent(): JComponent {
        mySettingsComponent = SettingComponent()
        mySettingsComponent!!.setGhesHostnameText(SettingsState.instance.state.ghesHostname)
        mySettingsComponent!!.setGhesTokenText(SettingsState.instance.state.ghesPat)
        return mySettingsComponent!!.getPanel()
    }

    override fun isModified(): Boolean =
            listOf(
                mySettingsComponent!!.getGhesHostnameText() != SettingsState.instance.state.ghesHostname,
                mySettingsComponent!!.getGhesTokenText() != SettingsState.instance.state.ghesPat,
            ).any()

    override fun apply() {
        val settings: SettingsState = SettingsState.instance
        // Keep the PAT for backward compatibility but don't require user input for GitHub.com
        settings.state.ghesHostname = mySettingsComponent!!.getGhesHostnameText()
        settings.state.ghesPat = mySettingsComponent!!.getGhesTokenText()

        ApplicationManager.getApplication().messageBus.syncPublisher(SETTINGS_SAVED_TOPIC).settingsSaved()

    }

    override fun reset() {
        mySettingsComponent?.setGhesHostnameText(SettingsState.instance.state.ghesHostname)
        mySettingsComponent?.setGhesTokenText(SettingsState.instance.state.ghesPat)
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }

    override fun dispose() {
        mySettingsComponent = null
    }

    companion object {
        val SETTINGS_SAVED_TOPIC = Topic.create("SettingsSaved", SettingsSavedListener::class.java)
    }
}