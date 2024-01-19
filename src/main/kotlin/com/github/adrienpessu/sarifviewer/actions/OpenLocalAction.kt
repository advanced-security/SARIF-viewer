package com.github.adrienpessu.sarifviewer.actions

import com.github.adrienpessu.sarifviewer.toolWindow.SarifViewerWindowFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class OpenLocalAction : AnAction("Open local Sarif file") {

    var myToolWindow: SarifViewerWindowFactory.MyToolWindow? = null

    override fun actionPerformed(e: AnActionEvent) {
        myToolWindow?.openLocalFile()
    }
}

