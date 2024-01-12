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
        return ghTokenText.getText()
    }

    fun setGhTokenText(newText: String) {
        ghTokenText.setText(newText)
    }

    fun getGhesHostnameText(): String {
        return ghesHostnameText.getText()
    }

    fun getGhesTokenText(): String {
        return ghesTokenText.getText()
    }

    fun setGhesHostnameText(newText: String) {
        ghesHostnameText.setText(newText)
    }

    fun setGhesTokenText(newText: String) {
        ghesTokenText.setText(newText)
    }
}