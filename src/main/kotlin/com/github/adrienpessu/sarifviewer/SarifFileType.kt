package com.github.adrienpessu.sarifviewer

import com.github.adrienpessu.sarifviewer.toolWindow.SarifViewerWindowFactory
import com.intellij.json.JsonFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.IconManager
import com.intellij.ui.components.JBPanel
import javax.swing.Icon

object SarifFileType : JsonFileType() {
    override fun getName() = "SARIF"
    override fun getDescription() = "SARIF file"
    override fun getDefaultExtension() = "sarif"
    override fun getIcon(): Icon = load("com.github.adrienpessu.sarifviewer/sarif.svg", -2129886975, 0);


    fun openFileInAssociatedApplication(project: Project?, file: VirtualFile): Boolean  {
        if (project == null) return false

        val toolWindow = ToolWindowManager
            .getInstance(project)
            .getToolWindow("Sarif viewer")


        toolWindow?.contentManager?.selectedContent?.component
            ?.let { component ->
                if (component is SarifViewerWindowFactory.MyToolWindow) {
                    component.openFile(project,file.toNioPath().toFile())
                }
            }

        toolWindow?.show() // opens and focuses the tool window
        return true
    }

    private fun load(path: String, cacheKey: Int, flags: Int): Icon {
        return IconManager.getInstance().getIcon(path, SarifFileType::class.java)
    }

    fun useNativeIcon(): Boolean = false

}