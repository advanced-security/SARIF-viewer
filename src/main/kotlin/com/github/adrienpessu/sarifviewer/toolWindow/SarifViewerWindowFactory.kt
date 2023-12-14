package com.github.adrienpessu.sarifviewer.toolWindow

import com.contrastsecurity.sarif.SarifSchema210
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.adrienpessu.sarifviewer.configurable.Settings
import com.github.adrienpessu.sarifviewer.configurable.SettingsState
import com.github.adrienpessu.sarifviewer.exception.SarifViewerException
import com.github.adrienpessu.sarifviewer.models.BranchItemComboBox
import com.github.adrienpessu.sarifviewer.models.Leaf
import com.github.adrienpessu.sarifviewer.services.SarifService
import com.github.adrienpessu.sarifviewer.utils.GitHubInstance
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import git4idea.GitLocalBranch
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import java.awt.Component
import java.awt.Desktop
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.File
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.text.html.HTMLEditorKit
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel


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

        private var localMode = false
        private val service = toolWindow.project.service<SarifService>()
        private val project = toolWindow.project
        private var main = ScrollPaneFactory.createScrollPane()
        private val details = JBTabbedPane()
        private val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, main, details)
        private var sarif: SarifSchema210 = SarifSchema210()
        private var myList = JTree()
        private var selectList = ComboBox(arrayOf(BranchItemComboBox(0, "main", "", "")))
        private val refreshButton: JButton = JButton("Refresh from GH")
        private val infos = JEditorPane()
        private val steps = JEditorPane()
        private val errorField = JLabel("Error message here ")
        private val errorToolbar = JToolBar("", JToolBar.HORIZONTAL)
        private var sarifGitHubRef = ""

        fun getContent() = JBPanel<JBPanel<*>>().apply {

            manageTreeIcons()
            buildSkeleton()

            val messageBus = project.messageBus

            messageBus.connect().subscribe(Settings.SETTINGS_SAVED_TOPIC, object : Settings.SettingsSavedListener {
                override fun settingsSaved() {
                    if (!localMode) {
                        clearJSplitPane()

                        val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
                        if (repository != null) {
                            loadDataAndUI(repository)
                        } else {
                            add(JLabel("No Git repository found"))
                        }
                    }
                }
            })

            messageBus.connect().subscribe(GitRepository.GIT_REPO_CHANGE, object : GitRepositoryChangeListener {
                override fun repositoryChanged(repository: GitRepository) {
                    if (!localMode) {
                        clearJSplitPane()
                        loadDataAndUI(repository)
                        splitPane.topComponent
                    }
                }
            })
        }

        private fun JBPanel<JBPanel<*>>.loadDataAndUI(repository: GitRepository, refreshCombo: Boolean = true) {
            val currentBranch = repository.currentBranch

            val remote = repository.remotes.firstOrNull {
                GitHubInstance.extractHostname(it.firstUrl) in
                        setOf(GitHubInstance.DOT_COM.hostname, SettingsState.instance.pluginState.ghesHostname)
            }

            val github: GitHubInstance? = GitHubInstance.fromRemoteUrl(remote?.firstUrl.orEmpty())
            if (github == null) {
                displayError("Could not find a configured GitHub instance that matches $remote")
                return
            }

            if (github == GitHubInstance.DOT_COM) {
                github.token = SettingsState.instance.pluginState.pat
            } else if (github.hostname == SettingsState.instance.pluginState.ghesHostname) {
                github.token = SettingsState.instance.pluginState.ghesPat
            }

            val repositoryFullName = github.extractRepoNwo(remote?.firstUrl)
            if (repositoryFullName == null) {
                displayError("Could not determine repository owner and name from remote URL: $remote")
                return
            }

            if (refreshCombo) {
                sarifGitHubRef = "refs/heads/${currentBranch?.name ?: "refs/heads/main"}"
            }

            if (github.token == SettingsState().pluginState.pat || github.token.isEmpty()) {
                displayError("No GitHub PAT found for ${github.hostname}")
                return
            }

            if (repositoryFullName.isNotEmpty() && currentBranch?.name?.isNotEmpty() == true) {
                try {
                    if (refreshCombo) {
                        populateCombo(currentBranch, github, repositoryFullName)
                    }

                    val map = extractSarif(github, repositoryFullName)
                    if (map.isEmpty()) {
                        displayError("No SARIF file found for the repository $repositoryFullName and ref $sarifGitHubRef")
                    } else {
                        thisLogger().info("Load result for the repository $repositoryFullName and ref $sarifGitHubRef")
                        buildContent(map, github, repositoryFullName, currentBranch)
                    }
                } catch (e: SarifViewerException) {
                    add(JLabel(e.message))
                    thisLogger().warn(e.message)
                    return
                }


            } else {
                displayError("No remote found")
            }
        }

        private fun displayError(message: String) {
            clearJSplitPane()
            errorToolbar.isVisible = true
            errorField.text = message

            NotificationGroupManager.getInstance()
                .getNotificationGroup("SARIF viewer")
                .createNotification(message, NotificationType.ERROR)
                .notify(project)

            thisLogger().info(message)
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


            errorToolbar.setSize(100, 10)
            errorToolbar.isFloatable = false
            errorToolbar.isRollover = true
            errorToolbar.alignmentX = Component.LEFT_ALIGNMENT
            errorToolbar.add(errorField)
            errorToolbar.isVisible = false
            add(errorToolbar)

            val jToolBar = JToolBar("", JToolBar.HORIZONTAL)
            jToolBar.setSize(100, 10)
            jToolBar.isFloatable = false
            jToolBar.isRollover = true
            jToolBar.alignmentX = Component.LEFT_ALIGNMENT
            jToolBar.add(refreshButton)

            selectList.addActionListener(ActionListener() { event ->
                val comboBox = event.source as JComboBox<*>
                if (event.actionCommand == "comboBoxChanged" && comboBox.selectedItem != null) {
                    val selectedOption = comboBox.selectedItem as BranchItemComboBox
                    sarifGitHubRef = if (selectedOption.prNumber != 0) {
                        "refs/pull/${selectedOption.prNumber}/merge"
                    } else {
                        "refs/heads/${selectedOption.head}"
                    }

                    clearJSplitPane()
                    val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
                    if (repository != null) {
                        loadDataAndUI(repository, false)
                    } else {
                        add(JLabel("No Git repository found"))
                    }
                }
            })

            jToolBar.add(selectList)

            val localFileButton = JButton("📂")
            localFileButton.setSize(10, 10)
            localFileButton.addActionListener(object : ActionListener {
                override fun actionPerformed(e: ActionEvent?) {
                    val fileChooser = JFileChooser()
                    fileChooser.fileFilter = FileNameExtensionFilter("SARIF files", "sarif")
                    val returnValue = fileChooser.showOpenDialog(null)
                    if (returnValue == JFileChooser.APPROVE_OPTION) {
                        val selectedFile: File = fileChooser.selectedFile
                        val extractSarifFromFile = extractSarifFromFile(selectedFile)
                        treeBuilding(extractSarifFromFile)
                        localMode = true
                    }
                }
            })
            jToolBar.add(localFileButton)

            add(jToolBar)

            add(splitPane)

            details.isVisible = false
        }

        private fun buildContent(
            map: HashMap<String, MutableList<Leaf>>,
            github: GitHubInstance,
            repositoryFullName: String,
            currentBranch: GitLocalBranch
        ) {

            refreshButton.addActionListener(ActionListener() {
                localMode = false
                clearJSplitPane()
                populateCombo(currentBranch, github, repositoryFullName)
                val mapSarif = extractSarif(github, repositoryFullName)
                if (mapSarif.isEmpty()) {
                    displayError("No SARIF file found")
                } else {
                    thisLogger().info("Load result for the repository $repositoryFullName and branch ${currentBranch.name}")
                    buildContent(mapSarif, github, repositoryFullName, currentBranch)
                }
            })

            treeBuilding(map)
        }

        private fun treeBuilding(map: HashMap<String, MutableList<Leaf>>) {
            val root = DefaultMutableTreeNode(project.name)

            map.forEach { (key, value) ->
                val ruleNode = DefaultMutableTreeNode(key)
                value.forEach { location ->
                    val locationNode = DefaultMutableTreeNode(location)
                    ruleNode.add(locationNode)
                }
                root.add(ruleNode)
            }

            myList = JTree(root)

            myList.isRootVisible = false
            main = ScrollPaneFactory.createScrollPane(myList)

            details.isVisible = false

            splitPane.leftComponent = main
            splitPane.rightComponent = details

            myList.addTreeSelectionListener(object : TreeSelectionListener {
                override fun valueChanged(e: TreeSelectionEvent?) {
                    if (e != null && e.isAddedPath) {
                        val leaves = map[e.path.parentPath.lastPathComponent.toString()]
                        if (!leaves.isNullOrEmpty()) {
                            val leaf = leaves.first { it.address == e.path.lastPathComponent.toString() }

                            val githubAlertUrl = leaf.githubAlertUrl
                                .replace("api.", "")
                                .replace("api/v3/", "")
                                .replace("repos/", "")
                                .replace("code-scanning/alerts", "security/code-scanning")
                            val githubURL = "<a target=\"_BLANK\" href=\"$githubAlertUrl\">$githubAlertUrl</a>"

                            infos.contentType = "text/html"

                            infos.text =
                                "${leaf.leafName} <br/> Level: ${leaf.level} <br/>Rule's name: ${leaf.ruleName} <br/>Rule's description ${leaf.ruleDescription} <br/>Location ${leaf.location} <br/>GitHub alert number: ${leaf.githubAlertNumber} <br/>GitHub alert url ${githubURL}\n"

                            steps.read(leaf.steps.joinToString("<br/>") { step ->
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
            })
        }

        private fun manageTreeIcons() {
            val tmp = Files.createTempFile("warning", ".svg").toFile()
            val icon: Icon = ImageIcon(tmp.absolutePath)
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
            myList.model = DefaultTreeModel(DefaultMutableTreeNode())
            myList.updateUI()
            infos.text = ""
            steps.text = ""
            details.isVisible = false
            errorToolbar.isVisible = false
        }

        private fun extractSarif(
            github: GitHubInstance,
            repositoryFullName: String
        ): HashMap<String, MutableList<Leaf>> {
            sarif = service.loadSarifFile(github, repositoryFullName, sarifGitHubRef)
            var map = HashMap<String, MutableList<Leaf>>()
            if (sarif.runs?.isEmpty() == false) {
                map = service.analyseSarif(sarif)
            }

            return map
        }

        private fun extractSarifFromFile(
            file: File
        ): HashMap<String, MutableList<Leaf>> {
            // file to String
            val sarifString = file.readText(Charset.defaultCharset())
            val sarif = ObjectMapper().readValue(sarifString, SarifSchema210::class.java)
            var map = HashMap<String, MutableList<Leaf>>()
            if (sarif.runs?.isEmpty() == false) {
                map = service.analyseSarif(sarif)
            }

            return map
        }

        private fun populateCombo(
            currentBranch: GitLocalBranch?,
            github: GitHubInstance,
            repositoryFullName: String
        ) {
            selectList.removeAllItems()
            selectList.addItem(BranchItemComboBox(0, currentBranch?.name ?: "main", "", ""))
            val pullRequests =
                service.getPullRequests(github, repositoryFullName, sarifGitHubRef.split('/', limit = 3).last())
            if (pullRequests.isNotEmpty()) {
                pullRequests.forEach {
                    val currentPr = it as LinkedHashMap<*, *>
                    selectList.addItem(
                        BranchItemComboBox(
                            currentPr["number"] as Int,
                            (currentPr["base"] as LinkedHashMap<String, String>)["ref"] ?: "",
                            (currentPr["head"] as LinkedHashMap<String, String>)["ref"] ?: "",
                            currentPr["title"].toString()
                        )
                    )
                }
            }
        }
    }
}
