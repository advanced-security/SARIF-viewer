package com.github.adrienpessu.sarifviewer.services

import com.contrastsecurity.sarif.Result
import com.contrastsecurity.sarif.SarifSchema210
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.adrienpessu.sarifviewer.exception.SarifViewerException
import com.github.adrienpessu.sarifviewer.models.Leaf
import com.github.adrienpessu.sarifviewer.models.Root
import com.github.adrienpessu.sarifviewer.models.View
import com.github.adrienpessu.sarifviewer.utils.GitHubInstance
import com.google.common.base.Strings
import com.intellij.openapi.components.Service
import com.intellij.util.alsoIfNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.Comparator
import java.util.TreeMap


@Service(Service.Level.PROJECT)
class SarifService {

    fun getSarifFromGitHub(github: GitHubInstance, repositoryFullName: String, branchName: String): List<SarifSchema210?> {
        val analysisFromGitHub = getAnalysisFromGitHub(github, repositoryFullName, branchName)
        val objectMapper = ObjectMapper()
        val analysis: List<Root> = objectMapper.readValue(analysisFromGitHub)

        val ids = analysis.filter { it.commit_sha == analysis.first().commit_sha }.map { it.id }

        return ids.map { id ->
            val sarifFromGitHub = getSarifFromGitHub(github, repositoryFullName, id)
            val sarif: SarifSchema210 = objectMapper.readValue(sarifFromGitHub)
            sarif.alsoIfNull { SarifSchema210() }
        }
    }

    fun analyseResult(results: List<Result>): HashMap<String, MutableList<Leaf>> {
        val map = HashMap<String, MutableList<Leaf>>()
        results.forEach { result ->
            val element = leaf(result)
            val key = result.rule?.id ?: result.correlationGuid?.toString() ?: result.message.text
            if (map.containsKey(key)) {
                map[key]?.add(element)
            } else {
                map[key] = mutableListOf(element)
            }
        }
        return map

    }

    fun analyseSarif(sarif: SarifSchema210, view: View): MutableMap<String, MutableList<Leaf>> {

        when (view) {
            View.RULE -> {
                val map = HashMap<String, MutableList<Leaf>>()
                try {
                    sarif.runs.forEach { run ->
                        run?.results?.forEach { result ->
                            val element = leaf(result)
                            val key = result.rule?.id ?: result.correlationGuid?.toString() ?: result.message.text
                            if (map.containsKey(key)) {
                                map[key]?.add(element)
                            } else {
                                map[key] = mutableListOf(element)
                            }
                        }
                    }
                } catch (e: Exception) {
                    throw SarifViewerException.INVALID_SARIF
                }
                return map
            }

            View.LOCATION -> {
                val map = HashMap<String, MutableList<Leaf>>()
                try {
                    sarif.runs.forEach { run ->
                        run?.results?.forEach { result ->
                            val element = leaf(result)
                            val key = result.locations[0].physicalLocation.artifactLocation.uri
                            if (map.containsKey(key)) {
                                map[key]?.add(element)
                            } else {
                                map[key] = mutableListOf(element)
                            }
                        }
                    }
                } catch (e: Exception) {
                    throw SarifViewerException.INVALID_SARIF
                }
                return map
            }

            View.ALERT_NUMBER -> {
                val map = TreeMap<String, MutableList<Leaf>>();
                try {
                    sarif.runs.forEach { run ->
                        run?.results?.forEach { result ->
                            val element = leaf(result)
                            val key = if (Strings.isNullOrEmpty(element.githubAlertNumber)) {
                                "Missing alert number"
                            } else {
                                element.githubAlertNumber
                            }
                            if (map.containsKey(key)) {
                                map[key]?.add(element)
                            } else {
                                map[key] = mutableListOf(element)
                            }
                        }
                    }
                } catch (e: Exception) {
                    throw SarifViewerException.INVALID_SARIF
                }
                return map.toSortedMap(Comparator.comparingInt { k ->
                    try {
                        Integer.valueOf(k)
                    } catch (e: NumberFormatException) {
                        Integer.MIN_VALUE
                    }
                })
            }

            else -> {
                throw SarifViewerException.INVALID_VIEW
            }
        }
    }

    private fun leaf(result: Result): Leaf {
        val additionalProperties = result.properties?.additionalProperties ?: mapOf()
        val element = Leaf(
                leafName = result.message.text ?: "",
                address = "${result.locations[0].physicalLocation.artifactLocation.uri}:${result.locations[0].physicalLocation.region.startLine}",
                steps = result.codeFlows?.get(0)?.threadFlows?.get(0)?.locations?.map { "${it.location.physicalLocation.artifactLocation.uri}:${it.location.physicalLocation.region.startLine}" }
                        ?: listOf(),
                location = result.locations[0].physicalLocation.artifactLocation.uri,
                ruleId = result.ruleId,
                ruleName = result.rule?.id ?: "",
                ruleDescription = result.message.text ?: "",
                level = result.level.toString(),
                kind = result.kind.toString(),
                githubAlertNumber = additionalProperties["github/alertNumber"]?.toString() ?: "",
                githubAlertUrl = additionalProperties["github/alertUrl"]?.toString() ?: ""
        )
        return element
    }

    fun getPullRequests(github: GitHubInstance, repositoryFullName: String, branchName: String = "main"): List<*>? {
        val head = "${repositoryFullName.split("/")[0]}:$branchName"
        val connection = URL("${github.apiBase}/repos/$repositoryFullName/pulls?state=open&head=$head")
                .openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "GET"
            doInput = true
            doOutput = true

            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Authorization", "Bearer ${github.token}")
        }

        handleExceptions(connection)

        val response = connection.inputStream.bufferedReader().readText()

        connection.disconnect()
        return ObjectMapper().readValue(response, List::class.java)
    }

    private fun getAnalysisFromGitHub(
            github: GitHubInstance,
            repositoryFullName: String,
            branchName: String = "main"
    ): String {

        val s = "${github.apiBase}/repos/$repositoryFullName/code-scanning/analyses?ref=$branchName"
        val connection = URL(s)
                .openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "GET"
            doInput = true
            doOutput = true

            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Authorization", "Bearer ${github.token}")
        }

        handleExceptions(connection)

        val response = connection.inputStream.bufferedReader().readText()

        connection.disconnect()

        return response
    }

    private fun handleExceptions(connection: HttpURLConnection) {
        when (connection.responseCode) {
            401 -> {
                throw SarifViewerException.INVALID_PAT
            }

            404 -> {
                throw SarifViewerException.INVALID_REPOSITORY
            }

            422 -> {
                throw SarifViewerException.INVALID_BRANCH
            }

            403 -> {
                throw SarifViewerException.UNAUTHORIZED
            }
        }
    }

    private fun getSarifFromGitHub(github: GitHubInstance, repositoryFullName: String, analysisId: Int): String {
        val connection = URL("${github.apiBase}/repos/$repositoryFullName/code-scanning/analyses/$analysisId")
                .openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "GET"
            doInput = true
            doOutput = true

            setRequestProperty("Accept", "application/sarif+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Authorization", "Bearer ${github.token}")
        }

        handleExceptions(connection)

        val response = connection.inputStream.bufferedReader().readText()

        connection.disconnect()

        return response
    }
}
