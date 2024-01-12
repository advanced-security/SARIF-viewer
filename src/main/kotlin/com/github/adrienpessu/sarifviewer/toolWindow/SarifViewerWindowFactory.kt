package com.github.adrienpessu.sarifviewer.toolWindow

import com.contrastsecurity.sarif.Result
import com.contrastsecurity.sarif.SarifSchema210
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.adrienpessu.sarifviewer.actions.OpenLocalAction
import com.github.adrienpessu.sarifviewer.actions.RefreshAction
import com.github.adrienpessu.sarifviewer.configurable.Settings
import com.github.adrienpessu.sarifviewer.configurable.SettingsState
import com.github.adrienpessu.sarifviewer.exception.SarifViewerException
import com.github.adrienpessu.sarifviewer.models.BranchItemComboBox
import com.github.adrienpessu.sarifviewer.models.Leaf
import com.github.adrienpessu.sarifviewer.models.View
import com.github.adrienpessu.sarifviewer.services.SarifService
import com.github.adrienpessu.sarifviewer.utils.GitHubInstance
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import git4idea.GitLocalBranch
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionListener
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.DefaultTableModel
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

        init {
            val actionManager = ActionManager.getInstance()

            val openLocalFileAction = actionManager.getAction("OpenLocalFileAction")
            (openLocalFileAction as OpenLocalAction).myToolWindow = this
            val refreshAction = actionManager.getAction("RefreshAction")
            (refreshAction as RefreshAction).myToolWindow = this
            val actions = ArrayList<AnAction>()
            actions.add(openLocalFileAction)
            actions.add(refreshAction)

            toolWindow.setTitleActions(actions)
        }

        internal var github: GitHubInstance? = null
        internal var repositoryFullName: String? = null
        internal var currentBranch: GitLocalBranch? = null

        private var localMode = false
        private val service = toolWindow.project.service<SarifService>()
        private val project = toolWindow.project
        private var main = ScrollPaneFactory.createScrollPane()
        private val details = JBTabbedPane()
        private val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, main, details)
        private var myList = com.intellij.ui.treeStructure.Tree()
        private var comboBranchPR = ComboBox(arrayOf(BranchItemComboBox(0, "main", "", "")))
        private val tableInfos = JBTable(DefaultTableModel(arrayOf<Any>("Property", "Value"), 0))
        private val steps = JEditorPane()
        private val errorField = JLabel("Error message here ")
        private val errorToolbar = JToolBar("", JToolBar.HORIZONTAL)
        private val loadingPanel = JPanel()
        private var sarifGitHubRef = ""
        private var loading = false
        private var disableComboBoxEvent = false
        private var currentView = View.RULE
        private var cacheSarif: SarifSchema210? = null

        fun getContent() = JBPanel<JBPanel<*>>().apply {

            manageTreeIcons()
            buildSkeleton()

            val messageBus = project.messageBus

            messageBus.connect().subscribe(Settings.SETTINGS_SAVED_TOPIC, object : Settings.SettingsSavedListener {
                override fun settingsSaved() {
                    val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
                    if (!localMode) {
                        clearJSplitPane()
                        if (repository != null) {
                            val worker = object : SwingWorker<Unit, Unit>() {
                                override fun doInBackground() {
                                    toggleLoading()
                                    loadDataAndUI(repository)
                                    toggleLoading()
                                }
                            }
                            worker.execute()
                        }
                    }
                }
            })

            messageBus.connect().subscribe(GitRepository.GIT_REPO_CHANGE, object : GitRepositoryChangeListener {
                override fun repositoryChanged(repository: GitRepository) {
                    if (!localMode) {
                        clearJSplitPane()
                        if (repository != null) {
                            toggleLoading()
                            loadDataAndUI(repository)
                            toggleLoading()
                        }
                    }
                }
            })
        }

        private fun JBPanel<JBPanel<*>>.loadDataAndUI(
            repository: GitRepository,
            selectedCombo: BranchItemComboBox? = null
        ) {
            currentBranch = repository.currentBranch

            val remote = repository.remotes.firstOrNull {
                GitHubInstance.extractHostname(it.firstUrl) in
                        setOf(GitHubInstance.DOT_COM.hostname, SettingsState.instance.pluginState.ghesHostname)
            }

            github = GitHubInstance.fromRemoteUrl(remote?.firstUrl.orEmpty())
            if (github == null) {
                displayError("Could not find a configured GitHub instance that matches $remote")
                return
            }

            if (github == GitHubInstance.DOT_COM) {
                github!!.token = SettingsState.instance.pluginState.pat
            } else if (github!!.hostname == SettingsState.instance.pluginState.ghesHostname) {
                github!!.token = SettingsState.instance.pluginState.ghesPat
            }

            repositoryFullName = github!!.extractRepoNwo(remote?.firstUrl)
            if (repositoryFullName == null) {
                displayError("Could not determine repository owner and name from remote URL: $remote")
                return
            }

            if (selectedCombo == null) {
                sarifGitHubRef = "refs/heads/${currentBranch?.name ?: "refs/heads/main"}"
            }

            if (github!!.token == SettingsState().pluginState.pat || github!!.token.isEmpty()) {
                displayError("No GitHub PAT found for ${github!!.hostname}")
                return
            }

            if (repositoryFullName!!.isNotEmpty() && currentBranch?.name?.isNotEmpty() == true) {
                try {
                    if (selectedCombo == null) {
                        populateCombo(currentBranch, github!!, repositoryFullName!!)
                    }

                    val map = extractSarif(github!!, repositoryFullName!!, selectedCombo?.head)
                    if (map.isEmpty()) {
                        emptyNode(map, repositoryFullName)
                    } else {
                        thisLogger().info("Load result for the repository $repositoryFullName and ref $sarifGitHubRef")
                    }
                    buildContent(map)
                } catch (e: SarifViewerException) {
                    thisLogger().warn(e.message)
                    displayError(e.message)
                    return
                }


            } else {
                displayError("No remote found")
            }
        }

        private fun emptyNode(
            map: HashMap<String, MutableList<Leaf>>,
            repositoryFullName: String?
        ) {
            val element = Leaf(
                leafName = "",
                address = "",
                steps = listOf(),
                location = "",
                ruleId = "",
                ruleName = "",
                ruleDescription = "",
                level = "",
                kind = "",
                githubAlertNumber = "",
                githubAlertUrl = "",
            )
            map["No SARIF file found for the repository $repositoryFullName and ref $sarifGitHubRef"] =
                listOf(element).toMutableList()
        }

        private fun toggleLoading(forcedValue: Boolean? = null) {
            loading = forcedValue ?: !loading
            loadingPanel.isVisible = loading
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

//            infos.addHyperlinkListener(object : HyperlinkListener {
//                override fun hyperlinkUpdate(hle: HyperlinkEvent?) {
//                    if (HyperlinkEvent.EventType.ACTIVATED == hle?.eventType && hle?.description != null) {
//                        Desktop.getDesktop().browse(URI(hle.description))
//                    }
//                }
//            })
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

            // Add the table to a scroll pane
            val scrollPane = JScrollPane(tableInfos)

            details.addTab("Infos", scrollPane)
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
            jToolBar.isFloatable = false
            jToolBar.isRollover = true
            jToolBar.alignmentX = Component.LEFT_ALIGNMENT

            val viewComboBox = ComboBox(View.views)

            viewComboBox.maximumSize = Dimension(100, 30)

            viewComboBox.selectedItem = currentView

            viewComboBox.addActionListener(ActionListener() { event ->
                if (event.source is ComboBox<*>) {
                    val link = event.source as ComboBox<View>
                    val selectedItem = link.selectedItem as View
                    if (event.actionCommand == "comboBoxChanged" && !DumbService.isDumb(project) && selectedItem.key != currentView.key) {
                        val worker = object : SwingWorker<Unit, Unit>() {
                            override fun doInBackground() {
                                toggleLoading()
                                currentView = selectedItem
                                clearJSplitPane()
                                var map = HashMap<String, MutableList<Leaf>>()
                                if (localMode) {
                                    if (cacheSarif?.runs?.isEmpty() == false) {
                                        map = service.analyseSarif(cacheSarif!!, currentView)
                                    }
                                } else {
                                    map = extractSarif(github!!, repositoryFullName!!, sarifGitHubRef)
                                    treeBuilding(map)
                                }
                                treeBuilding(map)
                                toggleLoading()

                            }
                        }
                        worker.execute()
                    }
                }
            })
            jToolBar.add(viewComboBox)

            val jLabel = JLabel("Branch/PR: ")
            jLabel.maximumSize = Dimension(100, jToolBar.preferredSize.height)

            jToolBar.add(jLabel)

            comboBranchPR.addActionListener(ActionListener() { event ->
                val comboBox = event.source as JComboBox<*>
                if (event.actionCommand == "comboBoxChanged" && comboBox.selectedItem != null
                    && !disableComboBoxEvent && !DumbService.isDumb(project)
                ) {
                    val selectedOption = comboBox.selectedItem as BranchItemComboBox
                    sarifGitHubRef = if (selectedOption.prNumber != 0) {
                        "refs/pull/${selectedOption.prNumber}/merge"
                    } else {
                        "refs/heads/${selectedOption.head}"
                    }

                    clearJSplitPane()
                    val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
                    if (repository != null) {
                        // Create a SwingWorker to perform the time-consuming task in a separate thread
                        val worker = object : SwingWorker<Unit, Unit>() {
                            override fun doInBackground() {
                                toggleLoading(true)
                                loadDataAndUI(repository, selectedOption)
                                toggleLoading(false)
                            }
                        }
                        worker.execute()
                    } else {
                        add(JLabel("No Git repository found"))
                    }
                }
            })

            jToolBar.add(comboBranchPR)
            add(jToolBar)

            loadingPanel.layout = BoxLayout(loadingPanel, BoxLayout.Y_AXIS)
            loadingPanel.add(JLabel("Loading..."))
            loadingPanel.add(JLabel("Please wait..."))
            loadingPanel.isVisible = false
            add(loadingPanel)

            add(splitPane)

            details.isVisible = false
        }

        private fun buildContent(
            map: HashMap<String, MutableList<Leaf>>
        ) {
            treeBuilding(map)
        }

        fun openLocalFile() {
            val fileChooser = JFileChooser()
            fileChooser.fileFilter = FileNameExtensionFilter("SARIF files", "sarif")
            SwingUtilities.invokeLater {
                val returnValue = fileChooser.showOpenDialog(null)
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    val selectedFile: File = fileChooser.selectedFile
                    val extractSarifFromFile = extractSarifFromFile(selectedFile)
                    treeBuilding(extractSarifFromFile)
                    localMode = true
                }
            }
        }

        fun refresh(
            currentBranch: GitLocalBranch,
            github: GitHubInstance,
            repositoryFullName: String
        ) {
            localMode = false
            val worker = object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    toggleLoading(true)
                    clearJSplitPane()
                    populateCombo(currentBranch, github, repositoryFullName)
                    val mapSarif = extractSarif(github, repositoryFullName)
                    toggleLoading(false)
                    if (mapSarif.isEmpty()) {
                        emptyNode(mapSarif, repositoryFullName)
                    } else {
                        thisLogger().info("Load result for the repository $repositoryFullName and branch ${currentBranch.name}")
                    }
                    buildContent(mapSarif)
                }
            }
            worker.execute()
        }

        private fun treeBuilding(map: HashMap<String, MutableList<Leaf>>) {
            val root = DefaultMutableTreeNode(project.name)

            map.forEach { (key, value) ->
                val ruleNode = DefaultMutableTreeNode("$key (${value.size})")
                value.forEach { location ->
                    val locationNode = DefaultMutableTreeNode(location)
                    ruleNode.add(locationNode)
                }
                root.add(ruleNode)
            }

            myList = com.intellij.ui.treeStructure.Tree(root)

            myList.isRootVisible = false
            myList.showsRootHandles = true
            main = ScrollPaneFactory.createScrollPane(myList)

            details.isVisible = false

            splitPane.leftComponent = main
            splitPane.rightComponent = details

            myList.addTreeSelectionListener(object : TreeSelectionListener {
                override fun valueChanged(e: TreeSelectionEvent?) {
                    if (e != null && e.isAddedPath) {
                        val leaves = map[e.path.parentPath.lastPathComponent.toString().split(" ").first()]
                        if (!leaves.isNullOrEmpty()) {
                            val leaf = try {
                                leaves.first { it.address == ((e.path.lastPathComponent as DefaultMutableTreeNode).userObject as Leaf).address }
                            } catch (e: Exception) {
                                leaves.first()
                            }

                            val githubAlertUrl = leaf.githubAlertUrl
                                .replace("api.", "")
                                .replace("api/v3/", "")
                                .replace("repos/", "")
                                .replace("code-scanning/alerts", "security/code-scanning")

                            tableInfos.clearSelection()
                            // Create a table model with "Property" and "Value" columns
                            val defaultTableModel: DefaultTableModel =
                                object : DefaultTableModel(arrayOf<Any>("Property", "Value"), 0) {
                                    override fun isCellEditable(row: Int, column: Int): Boolean {
                                        return false
                                    }
                                }
                            tableInfos.model = defaultTableModel
                            // Set a custom cell renderer for the "Value" column
                            tableInfos.columnModel.getColumn(1).setCellRenderer(UrlCellRenderer())

                            // Add some data
                            defaultTableModel.addRow(arrayOf<Any>("Name", leaf.leafName))
                            defaultTableModel.addRow(arrayOf<Any>("Level", leaf.level))
                            defaultTableModel.addRow(arrayOf<Any>("Rule's name", leaf.ruleName))
                            defaultTableModel.addRow(arrayOf<Any>("Rule's description", leaf.ruleDescription))
                            defaultTableModel.addRow(arrayOf<Any>("Location", leaf.location))
                            defaultTableModel.addRow(arrayOf<Any>("GitHub alert number", leaf.githubAlertNumber))
                            defaultTableModel.addRow(arrayOf<Any>("GitHub alert url", githubAlertUrl))
                            tableInfos.updateUI()

                            steps.read(leaf.steps.joinToString(" ", "<ul>", "</ul>") { step ->
                                "<li><a href=\"$step\">${step.split("/").last()}</a></li>"
                            }.byteInputStream(Charset.defaultCharset()), HTMLEditorKit::class.java)

                            steps.contentType = "text/html"

                            details.isVisible = true
                            openFile(
                                project,
                                leaf.location,
                                leaf.address.split(":")[1].toInt(),
                                0,
                                leaf.level,
                                leaf.ruleDescription
                            )

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

        private fun openFile(
            project: Project,
            path: String,
            lineNumber: Int,
            columnNumber: Int = 0,
            level: String = "",
            ruleDescription: String = ""
        ) {

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
                    FileDocumentManager.getInstance().getDocument(virtualFile)?.let { document ->
                        val lineStartOffset = document.getLineStartOffset(lineNumber - 1)
                        val lineEndOffset = document.getLineEndOffset(lineNumber - 1)
                        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
                        editor.caretModel.moveToOffset(lineStartOffset)
                        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                        editor.selectionModel.setSelection(lineStartOffset, lineEndOffset)

                        val messageType = when (level) {
                            "error" -> MessageType.ERROR
                            "warning" -> MessageType.WARNING
                            else -> MessageType.INFO
                        }

                        // add a balloon on the selection
                        val balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
                            ruleDescription,
                            messageType,
                            null
                        ).createBalloon()
                        balloon.show(
                            RelativePoint(
                                editor.contentComponent,
                                editor.visualPositionToXY(editor.caretModel.visualPosition)
                            ), Balloon.Position.above
                        )

                    }

                }
        }

        private fun clearJSplitPane() {
            if (myList.components != null) {
                SwingUtilities.invokeLater {
                    myList.model = DefaultTreeModel(DefaultMutableTreeNode())
                    myList.updateUI()
                }
            }

            tableInfos.clearSelection()
            tableInfos.updateUI()
            steps.text = ""
            details.isVisible = false
            errorToolbar.isVisible = false
        }

        private fun extractSarif(
            github: GitHubInstance,
            repositoryFullName: String,
            base: String? = null
        ): HashMap<String, MutableList<Leaf>> {
            val sarifs = service.getSarifFromGitHub(github, repositoryFullName, sarifGitHubRef).filterNotNull()
            var map = HashMap<String, MutableList<Leaf>>()
            val results = sarifs.flatMap { it.runs?.get(0)?.results ?: emptyList() }
            if (sarifs.isNotEmpty()) {
                if (sarifGitHubRef.startsWith("refs/pull/") && base != null) {
                    val resultsToDisplay = ArrayList<Result>()
                    val sarifMainBranch = service.getSarifFromGitHub(github, repositoryFullName, base).filterNotNull()
                    val mainResults: List<Result> = sarifMainBranch.flatMap { it.runs?.get(0)?.results ?: emptyList() }

                    for (currentResult in results) {
                        if (mainResults.none {
                                it.ruleId == currentResult.ruleId
                                        && ("${currentResult.locations[0].physicalLocation.artifactLocation.uri}:${currentResult.locations[0].physicalLocation.region.startLine}" == "${it.locations[0].physicalLocation.artifactLocation.uri}:${it.locations[0].physicalLocation.region.startLine}")
                            }) {
                            resultsToDisplay.add(currentResult)
                        }
                    }
                    map = service.analyseResult(resultsToDisplay)
                } else {
                    map = sarifs.map { service.analyseSarif(it, currentView) }
                        .reduce { acc, item -> acc.apply { putAll(item) } }
                }
            }

            return map
        }

        private fun extractSarifFromFile(
            file: File
        ): HashMap<String, MutableList<Leaf>> {
            // file to String
            val sarifString = file.readText(Charset.defaultCharset())
            val sarif = ObjectMapper().readValue(sarifString, SarifSchema210::class.java)
            cacheSarif = sarif
            var map = HashMap<String, MutableList<Leaf>>()
            if (sarif.runs?.isEmpty() == false) {
                map = service.analyseSarif(sarif, currentView)
            }

            return map
        }

        private fun populateCombo(
            currentBranch: GitLocalBranch?,
            github: GitHubInstance,
            repositoryFullName: String
        ) {
            disableComboBoxEvent = true
            comboBranchPR.removeAllItems()
            comboBranchPR.addItem(BranchItemComboBox(0, currentBranch?.name ?: "main", "", ""))
            val pullRequests =
                service.getPullRequests(github, repositoryFullName, sarifGitHubRef.split('/', limit = 3).last())
            if (pullRequests?.isNotEmpty() == true) {
                pullRequests.forEach {
                    val currentPr = it as LinkedHashMap<*, *>
                    comboBranchPR.addItem(
                        BranchItemComboBox(
                            currentPr["number"] as Int,
                            (currentPr["base"] as LinkedHashMap<String, String>)["ref"] ?: "",
                            (currentPr["head"] as LinkedHashMap<String, String>)["ref"] ?: "",
                            currentPr["title"].toString(),
                            (currentPr["head"] as LinkedHashMap<String, String>)["commit_sha"] ?: ""
                        )
                    )
                }
            }
            disableComboBoxEvent = false
        }
    }
}
