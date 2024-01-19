package com.github.adrienpessu.sarifviewer.configurable

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel


class SettingComponent {
    private var myMainPanel: JPanel? = null
    private val ghTokenText = JBPasswordField()
    private val ghesHostnameText = JBTextField()
    private val ghesTokenText = JBPasswordField()

    init {
        myMainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel("GitHub PAT "), ghTokenText, 1, false)
                .addSeparator()
                .addLabeledComponent(JBLabel("GHE Hostname"), ghesHostnameText, 2, false)
                .addLabeledComponent(JBLabel("GHE Token"), ghesTokenText, 3, false)
                .addComponentFillVertically(JPanel(), 0)
                .getPanel()
    }


    fun getPanel(): JPanel {
        return myMainPanel!!
    }

    fun getPreferredFocusedComponent(): JComponent {
        return ghTokenText
    }

    fun getGhTokenText(): String {
        return ghTokenText.text
    }

    fun setGhTokenText(newText: String) {
        ghTokenText.text = newText
    }

    fun getGhesHostnameText(): String {
        return ghesHostnameText.text
    }

    fun getGhesTokenText(): String {
        return ghesTokenText.text
    }

    fun setGhesHostnameText(newText: String) {
        ghesHostnameText.text = newText
    }

    fun setGhesTokenText(newText: String) {
        ghesTokenText.text = newText
    }
}