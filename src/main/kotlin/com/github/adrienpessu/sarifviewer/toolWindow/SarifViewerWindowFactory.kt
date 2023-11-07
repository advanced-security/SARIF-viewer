package com.github.adrienpessu.sarifviewer.toolWindow

import com.contrastsecurity.sarif.SarifSchema210
import com.github.adrienpessu.sarifviewer.configurable.Settings
import com.github.adrienpessu.sarifviewer.configurable.SettingsState
import com.github.adrienpessu.sarifviewer.exception.SarifViewerException
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
import git4idea.GitLocalBranch
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import java.awt.Component
import java.awt.Desktop
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.Path
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.text.html.HTMLEditorKit
import javax.swing.tree.DefaultMutableTreeNode


class SarifViewerWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), project.name, false)

        toolWindow.contentManager.addContent(content)

    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(toolWindow: ToolWindow) {

        private val service = toolWindow.project.service<SarifService>()
        private val project = toolWindow.project
        private var main = ScrollPaneFactory.createScrollPane()
        private val details = JTabbedPane()
        private val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, main, details)
        private var sarif: SarifSchema210 = SarifSchema210()
        private var myList = JTree()
        private val refreshButton: JButton = JButton("Refresh")
        private val infos = JEditorPane()
        private val steps = JEditorPane()

        fun getContent() = JBPanel<JBPanel<*>>().apply {

            manageTreeIcons()
            buildSkeleton()

            val messageBus = project.messageBus

            messageBus.connect().subscribe(Settings.SETTINGS_SAVED_TOPIC, object : Settings.SettingsSavedListener {
                override fun settingsSaved() {
                    clearJSplitPane()

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
                    clearJSplitPane()
                    loadDataAndUI(repository)
                    splitPane.topComponent
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
                    val map = extractSarif(token, repositoryFullName, currentBranch)
                    if (map.isEmpty()) {
                        add(JLabel("No SARIF file found"))
                        thisLogger().warn("No SARIF file found")
                    } else {
                        thisLogger().info("Load result for the repository $repositoryFullName and branch ${currentBranch.name}")
                        buildContent(map, token, repositoryFullName, currentBranch)
                    }
                } catch (e: SarifViewerException) {
                    add(JLabel(e.message))
                    thisLogger().warn(e.message)
                    return
                }


            } else {
                add(JLabel("No remote found"))
            }
        }

        private fun JBPanel<JBPanel<*>>.buildSkeleton() {
            infos.isEditable = false
            infos.addHyperlinkListener(object : HyperlinkListener {
                override fun hyperlinkUpdate(hle: HyperlinkEvent?) {
                    if (HyperlinkEvent.EventType.ACTIVATED == hle?.eventType && hle?.description != null) {
                        Desktop.getDesktop().browse(URI(hle.description))
                    }
                }
            })
            steps.isEditable = false
            steps.addHyperlinkListener(object : HyperlinkListener {
                override fun hyperlinkUpdate(hle: HyperlinkEvent?) {
                    if (HyperlinkEvent.EventType.ACTIVATED == hle?.eventType) {
                        hle?.description.toString().split(":").let { location ->
                            openFile(project, location[0], location[1].toInt())
                        }
                    }
                }
            })

            details.addTab("Infos", infos)
            details.addTab("Steps", steps)

            layout = BoxLayout(this, BoxLayout.Y_AXIS)



            doLayout()

            val jToolBar = JToolBar("", JToolBar.HORIZONTAL)
            jToolBar.setSize(100, 10)
            jToolBar.isFloatable = false
            jToolBar.isRollover = true
            jToolBar.alignmentX = Component.LEFT_ALIGNMENT
            jToolBar.add(refreshButton)
            add(jToolBar)

            add(splitPane)

            details.isVisible = false
        }

        private fun buildContent(
            map: HashMap<String, MutableList<Leaf>>,
            token: String,
            repositoryFullName: String,
            currentBranch: GitLocalBranch
        ) {
            val root = DefaultMutableTreeNode(project.name)

            map.forEach() { (key, value) ->
                val ruleNode = DefaultMutableTreeNode(key)
                value.forEach() { location ->
                    val locationNode = DefaultMutableTreeNode(location)
                    ruleNode.add(locationNode)
                }
                root.add(ruleNode)
            }

            refreshButton.apply {
                addActionListener {
                    clearJSplitPane()
                    val map = extractSarif(token, repositoryFullName, currentBranch)
                    if (map.isEmpty()) {
                        add(JLabel("No SARIF file found"))
                        thisLogger().warn("No SARIF file found")
                    } else {
                        thisLogger().info("Load result for the repository $repositoryFullName and branch ${currentBranch.name}")
                        buildContent(map, token, repositoryFullName, currentBranch)
                    }
                }
            }


            myList = JTree(root)

            myList.isRootVisible = false
            val treeSpeedSearch = TreeSpeedSearch(myList)
            main = ScrollPaneFactory.createScrollPane(myList);

            details.isVisible = false

            splitPane.leftComponent = main
            splitPane.rightComponent = details

            treeSpeedSearch.component.addTreeSelectionListener(object : TreeSelectionListener {
                override fun valueChanged(e: TreeSelectionEvent?) {
                    if (e != null && e.isAddedPath) {
                        val leaves = map[e.path.parentPath.lastPathComponent.toString()]
                        if (!leaves.isNullOrEmpty()) {
                            val leaf = leaves.first { it.address == e.path.lastPathComponent.toString() }

                            val githubAlertUrl = leaf.githubAlertUrl.replace("api.", "").replace("repos/", "").replace("code-scanning/alerts", "security/code-scanning")
                            val githubURL = "<a target=\"_BLANK\" href=\"$githubAlertUrl\">$githubAlertUrl</a>"

                            infos.contentType = "text/html"

                            infos.text =
                                "${leaf.leafName} <br/> Level: ${leaf.level} <br/>Rule's name: ${leaf.ruleName} <br/>Rule's description ${leaf.ruleDescription} <br/>Location ${leaf.location} <br/>GitHub alert number: ${leaf.githubAlertNumber} <br/>GitHub alert url ${githubURL}\n"

                            steps.read( leaf.steps.joinToString("<br/>") { step ->
                                "<a href=\"$step\">${step.split("/").last()}</a>"
                            }.byteInputStream(Charset.defaultCharset()), HTMLEditorKit::class.java)

                            steps.contentType = "text/html"

                            details.isVisible = true
                            openFile(project, leaf.location, leaf.address.split(":")[1].toInt())

                            splitPane.setDividerLocation(0.5)

                        } else {
                            details.isVisible = false
                        }
                    }
                }
            });
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

            VirtualFileManager.getInstance().findFileByNioPath(Path.of("${project.basePath}/$path"))
                ?.let { virtualFile ->
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

        private fun clearJSplitPane() {
            myList = JTree()
            infos.text = ""
            steps.text = ""
            details.isVisible = false
        }

        private fun extractSarif(
            token: String,
            repositoryFullName: String,
            currentBranch: GitLocalBranch
        ): HashMap<String, MutableList<Leaf>> {
            sarif = service.loadSarifFile(token, repositoryFullName, currentBranch.name)
            var map = HashMap<String, MutableList<Leaf>>()
            if (sarif.runs?.isEmpty() == false) {
                map = service.analyseSarif(sarif)
            }

            return map
        }
    }
}
