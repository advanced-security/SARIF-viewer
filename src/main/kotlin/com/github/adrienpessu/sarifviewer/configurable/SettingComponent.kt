package com.github.adrienpessu.sarifviewer.configurable

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.JToggleButton


class SettingComponent {
    private var myMainPanel: JPanel? = null
    private val ghesHostnameText = JBTextField()
    private var ghesTokenTextField: JTextField  = JBTextField()
    private var ghesTokenPasswordField: JTextField  = JBPasswordField()
    private val toggleButton = JToggleButton("Show/Hide GHES PAT")

    private var isGhesTokenVisible: Boolean = false

    init {

        ghesTokenTextField.isVisible = isGhesTokenVisible
        myMainPanel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("GitHub.com Authentication"))
            .addComponent(JBLabel("Authentication is handled automatically through IntelliJ's GitHub integration."))
            .addComponent(JBLabel("Go to Settings > Version Control > GitHub to configure authentication."))
            .addSeparator(48)
            .addComponent(JBLabel("GHES Hostname "))
            .addComponent(ghesHostnameText)
            .addComponent(JBLabel("GHES PAT "))
            .addComponent(ghesTokenTextField)
            .addComponent(ghesTokenPasswordField)
            .addComponentFillVertically(JPanel(), 0)
            .addSeparator()
            .addLabeledComponent("", toggleButton, 1, false)
            .panel

        toggleButton.addActionListener {
            isGhesTokenVisible = !isGhesTokenVisible
            if (isGhesTokenVisible) {
                ghesTokenTextField.text = ghesTokenPasswordField.text
                ghesTokenTextField.isVisible = true
                ghesTokenPasswordField.isVisible = false
            } else {
                ghesTokenPasswordField.text = ghesTokenTextField.text
                ghesTokenTextField.isVisible = false
                ghesTokenPasswordField.isVisible = true
            }
            myMainPanel!!.revalidate() // Notify the layout manager
            myMainPanel!!.repaint() // Redraw the components
        }
    }


    fun getPanel(): JPanel {
        return myMainPanel!!
    }

    fun getPreferredFocusedComponent(): JComponent {
        return if (toggleButton.isSelected) {
            ghesTokenTextField
        } else {
            ghesTokenPasswordField
        }
    }

    fun getGhTokenText(): String {
        // Return empty string for GitHub.com PAT since it's no longer used
        return ""
    }

    fun setGhTokenText(newText: String) {
        // No-op since GitHub.com PAT is no longer used
    }

    fun getGhesHostnameText(): String {
        return ghesHostnameText.text
    }

    fun getGhesTokenText(): String {
        return if (isGhesTokenVisible) {
            ghesTokenTextField.text
        } else {
            ghesTokenPasswordField.text
        }
    }

    fun setGhesHostnameText(newText: String) {
        ghesHostnameText.text = newText
    }

    fun setGhesTokenText(newText: String) {
        ghesTokenTextField.text = newText
        ghesTokenPasswordField.text = newText
    }
}



