package com.github.adrienpessu.sarifviewer.actions

import com.github.adrienpessu.sarifviewer.exception.SarifViewerException
import com.github.adrienpessu.sarifviewer.toolWindow.SarifViewerWindowFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class RefreshAction : AnAction("Refresh from GitHub") {
    var myToolWindow: SarifViewerWindowFactory.MyToolWindow? = null
        set(value) {
            field = value
        }

    override fun actionPerformed(e: AnActionEvent) {
        val gitHubInstance = myToolWindow?.github?: throw SarifViewerException.INVALID_REPOSITORY
        val repositoryFullName = myToolWindow?.repositoryFullName?: throw SarifViewerException.INVALID_REPOSITORY
        val currentBranch = myToolWindow?.currentBranch?: throw SarifViewerException.INVALID_BRANCH

        myToolWindow?.refresh(currentBranch, gitHubInstance, repositoryFullName)
    }
}

