package com.github.adrienpessu.sarifviewer.actions

import com.github.adrienpessu.sarifviewer.toolWindow.SarifViewerWindowFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class RuleIDorDescriptionAction : AnAction("Toggle Rule ID or Description") {

    var myToolWindow: SarifViewerWindowFactory.MyToolWindow? = null

    override fun actionPerformed(e: AnActionEvent) {
        myToolWindow?.toggleRuleIdOrDescription()
    }
}