package com.github.adrienpessu.sarifviewer.configurable

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel


class SettingComponent {
    private var myMainPanel: JPanel? = null
    private val ghTokenText = JBTextField()

    init {
        myMainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel("GitHub PAT "), ghTokenText, 1, false)
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
}