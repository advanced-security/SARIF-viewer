package com.github.adrienpessu.sarifviewer.configurable

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.util.messages.Topic
import javax.swing.JComponent


class Settings: Configurable, Configurable.NoScroll, Disposable {
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
        mySettingsComponent!!.setGhTokenText(SettingsState.instance.state.pat)
        return mySettingsComponent!!.getPanel()
    }

    override fun isModified(): Boolean =
            !mySettingsComponent!!.getGhTokenText().equals(SettingsState.instance.state.pat)

    override fun apply() {
        val settings: SettingsState = SettingsState.instance
        settings.state.pat = mySettingsComponent!!.getGhTokenText()

        ApplicationManager.getApplication().messageBus.syncPublisher(SETTINGS_SAVED_TOPIC).settingsSaved()

    }

    override fun reset() {
        mySettingsComponent?.setGhTokenText(SettingsState.instance.state.pat)
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