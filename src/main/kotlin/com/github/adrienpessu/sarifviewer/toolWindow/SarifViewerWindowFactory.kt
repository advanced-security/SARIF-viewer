package com.github.adrienpessu.sarifviewer.toolWindow

import com.github.adrienpessu.sarifviewer.configurable.Settings
import com.github.adrienpessu.sarifviewer.configurable.SettingsState
import com.github.adrienpessu.sarifviewer.models.Leaf
import com.github.adrienpessu.sarifviewer.services.SarifService
import com.github.adrienpessu.sarifviewer.utils.Icons
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridLayout
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Path
import javax.swing.*
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode


class SarifViewerWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), "SARIF Viewer", false)

        toolWindow.contentManager.addContent(content)

    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(toolWindow: ToolWindow) {

        private val service = toolWindow.project.service<SarifService>()
        private val project = toolWindow.project

        fun getContent() = JBPanel<JBPanel<*>>().apply {

            manageTreeIcons()

            val messageBus = project.messageBus

            messageBus.connect().subscribe(Settings.SETTINGS_SAVED_TOPIC, object : Settings.SettingsSavedListener {
                override fun settingsSaved() {
                    components.forEach { remove(it) }
                    val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
                    if (repository != null) {
                        loadDataAndUI(repository)
                    } else {
                        add(JLabel("No Git repository found"))
                    }
                }
            })

            messageBus.connect().subscribe(GitRepository.GIT_REPO_CHANGE, object : GitRepositoryChangeListener {
                override fun repositoryChanged(repository: GitRepository) {
                    components.forEach { remove(it) }
                    loadDataAndUI(repository)
                }
            })


        }

        private fun JBPanel<JBPanel<*>>.loadDataAndUI(repository: GitRepository) {
            val currentBranch = repository.currentBranch
            val remote = repository.remotes.firstOrNull()
            val repositoryFullName = remote?.firstUrl?.replace("git@github.com:", "")?.replace(".git", "") ?: ""

            val token = SettingsState.instance.pluginState.pat

            if (token == SettingsState().pluginState.pat || token.isEmpty()) {
                add(JLabel("No GitHub PAT found"))
                thisLogger().warn("No GitHub PAT found")
                return
            }

            if (repositoryFullName.isNotEmpty() && currentBranch?.name?.isNotEmpty() == true) {
                try {
                    val sarif = service.loadSarifFile(token, repositoryFullName, currentBranch.name)

                    if (sarif.runs?.isEmpty() != false) {
                        add(JLabel("No SARIF file found"))
                        thisLogger().warn("No SARIF file found")
                    } else {
                        val map = service.analyseSarif(sarif)
                        thisLogger().info("Load result for the repository $repositoryFullName and branch ${currentBranch.name}")
                        loadOrRefreshUI(map)
                    }
                } catch (e: Exception) {
                    add(JLabel(e.message))
                    thisLogger().warn(e.message)
                    return
                }


            } else {
                add(JLabel("No remote found"))
            }
        }

        private fun JBPanel<JBPanel<*>>.loadOrRefreshUI(map: HashMap<String, MutableList<Leaf>>) {

            val root = DefaultMutableTreeNode(project.name)

            val detail = Box(BoxLayout.Y_AXIS)
            detail.preferredSize = detail.getMinimumSize()
            detail.setAlignmentY(Component.BOTTOM_ALIGNMENT);
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);
            val jLabel = JTextArea()
            jLabel.isEditable = false
            val steps = JTextArea()
            steps.isEditable = false


            detail.add(jLabel)
            detail.add(steps)

            map.forEach() { (key, value) ->
                val ruleNode = DefaultMutableTreeNode(key)
                value.forEach() { location ->
                    val locationNode = DefaultMutableTreeNode(location)

                    ruleNode.add(locationNode)
                }
                root.add(ruleNode)
            }

            layout = GridLayout(2, 1)

            doLayout()

            val myList = JTree(root)

            myList.showsRootHandles = false
            val treeSpeedSearch = TreeSpeedSearch(myList)
            val main = ScrollPaneFactory.createScrollPane(myList);

            add(main)

            treeSpeedSearch.component.addTreeSelectionListener(object : TreeSelectionListener {
                override fun valueChanged(e: TreeSelectionEvent?) {
                    println("valueChanged ${e.toString()} ")
                    if (e != null && e.isAddedPath) {
                        val leaves = map[e.path.parentPath.lastPathComponent.toString()]
                        if (leaves != null) {
                            val leaf = leaves.first { it.leafName == e.path.lastPathComponent.toString() }
                            println(leaf.leafName)
                            jLabel.text = "${leaf.leafName} \n Level: ${leaf.level} \nRule's name: ${leaf.ruleName} \nRule's description ${leaf.ruleDescription} \nLocation ${leaf.location} \nGitHub alert number: ${leaf.githubAlertNumber} \nGitHub alert url ${leaf.githubAlertUrl}\nSteps: \n${leaf.steps.joinToString { "$it \n" }}"
                            detail.isVisible = true

                            openFile(project, leaf.location, leaf.leafName.split(":")[1].toInt())

                        }
                    }
                }
            });

            add(detail, BorderLayout.SOUTH, 1)
            detail.isVisible = false
        }

        private fun manageTreeIcons() {
            val tmpFile: File = File.createTempFile("warning", ".svg")
            val writer = FileWriter(tmpFile)
            writer.write(Icons.ICON_WARNING)
            writer.close()

            val reader = BufferedReader(FileReader(tmpFile))
            reader.close()

            val icon: Icon = ImageIcon(tmpFile.absolutePath)
            UIManager.put("Tree.closedIcon", icon)
            UIManager.put("Tree.openIcon", icon)
            UIManager.put("Tree.leafIcon", icon)
        }

        private fun openFile(project: Project, path: String, lineNumber: Int, columnNumber: Int = 0) {

            VirtualFileManager.getInstance().findFileByNioPath(Path.of("${project.basePath}/$path"))?.let { virtualFile ->
                FileEditorManager.getInstance(project).openTextEditor(
                        OpenFileDescriptor(
                                project,
                                virtualFile,
                                lineNumber - 1,
                                columnNumber
                        ),
                        true // request focus to editor
                )
            }
        }
    }
}
