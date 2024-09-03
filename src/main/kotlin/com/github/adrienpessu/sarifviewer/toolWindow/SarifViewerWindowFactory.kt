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
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.jetbrains.rd.util.printlnError
import git4idea.GitLocalBranch
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import java.awt.Component
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.*
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
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
        private val tableSteps = JBTable(DefaultTableModel(arrayOf<Any>("Path"), 0))
        private val steps = JPanel()
        private val errorField = JLabel("Error message here ")
        private val errorToolbar = JToolBar("", JToolBar.HORIZONTAL)
        private val loadingPanel = JPanel()
        private var sarifGitHubRef = ""
        private var loading = false
        private var disableComboBoxEvent = false
        private var currentView = View.RULE
        private var cacheSarif: SarifSchema210? = null
        private var currentLeaf: Leaf? = null

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
            steps.layout = BoxLayout(steps, BoxLayout.Y_AXIS)
            tableSteps.size = Dimension(steps.width, steps.height)
            steps.add(tableSteps)

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
                            currentLeaf = try {
                                leaves.first { it.address == ((e.path.lastPathComponent as DefaultMutableTreeNode).userObject as Leaf).address }
                            } catch (e: Exception) {
                                leaves.first()
                            }

                            val githubAlertUrl = currentLeaf!!.githubAlertUrl
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

                            // Add some data
                            defaultTableModel.addRow(arrayOf<Any>("Name", currentLeaf!!.leafName))
                            defaultTableModel.addRow(arrayOf<Any>("Level", currentLeaf!!.level))
                            defaultTableModel.addRow(arrayOf<Any>("Rule's name", currentLeaf!!.ruleName))
                            defaultTableModel.addRow(arrayOf<Any>("Rule's description", currentLeaf!!.ruleDescription))
                            defaultTableModel.addRow(arrayOf<Any>("Location", currentLeaf!!.location))
                            defaultTableModel.addRow(
                                arrayOf<Any>(
                                    "GitHub alert number",
                                    currentLeaf!!.githubAlertNumber
                                )
                            )
                            defaultTableModel.addRow(
                                arrayOf<Any>(
                                    "GitHub alert url",
                                    "<a href=\"$githubAlertUrl\">$githubAlertUrl</a"
                                )
                            )

                            tableInfos.setDefaultRenderer(Object::class.java, object : DefaultTableCellRenderer() {
                                override fun getTableCellRendererComponent(
                                    table: JTable?,
                                    value: Any?,
                                    isSelected: Boolean,
                                    hasFocus: Boolean,
                                    row: Int,
                                    column: Int
                                ): Component {
                                    var c = super.getTableCellRendererComponent(
                                        table,
                                        value,
                                        isSelected,
                                        hasFocus,
                                        row,
                                        column
                                    )
                                    if (row == tableInfos.rowCount - 1 && column == tableInfos.columnCount - 1) {
                                        val url = tableInfos.getValueAt(row, column).toString()
                                        c = JLabel("<html><a href='$url'>$url</a></html>")
                                        c.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                                    }
                                    return c
                                }
                            })


                            tableInfos.addMouseListener(object : MouseAdapter() {
                                override fun mouseClicked(e: MouseEvent) {
                                    val row = tableInfos.rowAtPoint(e.point)
                                    val column = tableInfos.columnAtPoint(e.point)
                                    if (row == tableInfos.rowCount - 1) {
                                        if (column == tableInfos.columnCount - 1) {
                                            if (Desktop.isDesktopSupported() && Desktop.getDesktop()
                                                    .isSupported(Desktop.Action.BROWSE)
                                            ) {
                                                Desktop.getDesktop().browse(URI(githubAlertUrl))
                                            }
                                        }
                                    }
                                }
                            })

                            tableInfos.updateUI()

                            tableSteps.clearSelection()

                            tableSteps.model = DefaultTableModel(arrayOf<Any>("Path"), 0)

                            currentLeaf!!.steps.forEachIndexed { index, step ->
                                (tableSteps.model as DefaultTableModel).addRow(arrayOf<Any>("$index $step"))
                            }

                            tableSteps.addMouseListener(object : MouseAdapter() {
                                override fun mouseClicked(e: MouseEvent) {
                                    val row = tableInfos.rowAtPoint(e.point)
                                    // When the row can't be found, the method returns -1
                                    if (row != -1) {
                                        val path = currentLeaf!!.steps[row].split(":")
                                        openFile(project, path[0], path[1].toInt(), path[2].toInt())
                                    }
                                }
                            })

                            details.isVisible = true
                            val addr = currentLeaf!!.address.split(":")
                            openFile(
                                project,
                                currentLeaf!!.location,
                                addr[1].toInt(),
                                addr[2].toInt(),
                                currentLeaf!!.level,
                                currentLeaf!!.ruleId,
                                currentLeaf!!.ruleDescription
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
            rule: String = "",
            description: String = ""
        ) {

            val editor: Editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
            val inlayModel = editor.inlayModel

            inlayModel.getBlockElementsInRange(0, editor.document.textLength)
                .filter { it.renderer is MyCustomInlayRenderer }
                .forEach { it.dispose() }

            val virtualFile =
                VirtualFileManager.getInstance().findFileByNioPath(Path.of("${project.basePath}/$path"))
            if (virtualFile != null) {
                FileEditorManager.getInstance(project).openTextEditor(
                    OpenFileDescriptor(
                        project,
                        virtualFile,
                        lineNumber - 1,
                        columnNumber - 1
                    ),
                    true // request focus to editor
                )
                val editor: Editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
                val inlayModel = editor.inlayModel

                val offset = editor.document.getLineStartOffset(lineNumber - 1)

                val icon = when (level) {
                    "error" -> "🛑"
                    "warning" -> "⚠️"
                    "note" -> "📝"
                    else -> ""
                }
                val description = "$icon $rule: $description"
                if (description.isNotEmpty()) {
                    inlayModel.addBlockElement(offset, true, true, 1, MyCustomInlayRenderer(description))
                }
            } else {
                // display error message
                Notifications.Bus.notify(
                    Notification(
                        "Sarif viewer",
                        "File not found",
                        "Can't find the file ${project.basePath}/$path",
                        NotificationType.WARNING
                    ), project
                )
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
            tableSteps.clearSelection()
            tableSteps.updateUI()
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
