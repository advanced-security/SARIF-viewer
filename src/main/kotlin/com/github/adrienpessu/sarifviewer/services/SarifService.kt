package com.github.adrienpessu.sarifviewer.services

import com.contrastsecurity.sarif.SarifSchema210
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.adrienpessu.sarifviewer.exception.SarifViewerException
import com.github.adrienpessu.sarifviewer.models.Leaf
import com.github.adrienpessu.sarifviewer.models.Root
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.net.HttpURLConnection
import java.net.URL


@Service(Service.Level.PROJECT)
class SarifService {

    fun loadSarifFile(token: String, repositoryFullName: String, branchName: String): SarifSchema210 {
        val analysisFromGitHub = getAnalysisFromGitHub(token, repositoryFullName, branchName)
        val objectMapper = ObjectMapper()
        val analysis: List<Root> = objectMapper.readValue(analysisFromGitHub)
        if (analysis.isEmpty()) {
            return SarifSchema210()
        }
        val id = analysis.first().id

        val sarifFromGitHub = getSarifFromGitHub(token, repositoryFullName, id)

        return objectMapper.readValue(sarifFromGitHub, SarifSchema210::class.java)
    }

    fun analyseSarif(sarif: SarifSchema210): HashMap<String, MutableList<Leaf>> {
        val map = HashMap<String, MutableList<Leaf>>();

        sarif.runs.forEach { run ->
            run.results.forEach { result ->
                val properties = result.properties
                if (map.containsKey(result.rule.id)) {
                    map[result.rule.id]?.add(Leaf(
                            leafName = "${result.locations[0].physicalLocation.artifactLocation.uri}:${result.locations[0].physicalLocation.region.startLine}",
                            steps = result.codeFlows?.get(0)?.threadFlows?.get(0)?.locations?.map { "${it.location.physicalLocation.artifactLocation.uri}:${it.location.physicalLocation.region.startLine}" }
                                    ?: listOf(),
                            location = result.locations[0].physicalLocation.artifactLocation.uri,
                            ruleName = result.ruleId,
                            ruleDescription = result.message.text,
                            level = result.level.toString(),
                            githubAlertNumber = properties.additionalProperties["github/alertNumber"].toString(),
                            githubAlertUrl = properties.additionalProperties["github/alertUrl"].toString()
                    ))
                } else {
                    map[result.rule.id] = mutableListOf(Leaf(
                            leafName = "${result.locations[0].physicalLocation.artifactLocation.uri}:${result.locations[0].physicalLocation.region.startLine}",
                            steps = result.codeFlows?.get(0)?.threadFlows?.get(0)?.locations?.map { "${it.location.physicalLocation.artifactLocation.uri}:${it.location.physicalLocation.region.startLine}" }
                                    ?: listOf(),
                            location = result.locations[0].physicalLocation.artifactLocation.uri,
                            ruleName = result.ruleId,
                            ruleDescription = result.message.text,
                            level = result.level.toString(),
                            githubAlertNumber = properties.additionalProperties["github/alertNumber"].toString(),
                            githubAlertUrl = properties.additionalProperties["github/alertUrl"].toString()
                    ))
                }
            }
        }
        return map
    }

    private fun getAnalysisFromGitHub(token: String, repositoryFullName: String, branchName: String = "main"): String {

        val connection = URL("https://api.github.com/repos/$repositoryFullName/code-scanning/analyses?ref=refs/heads/$branchName")
                .openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "GET"
            doInput = true
            doOutput = true

            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Authorization", "Bearer $token")
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

    private fun getSarifFromGitHub(token: String, repositoryFullName: String, analysisId: Int): String {
        val connection = URL("https://api.github.com/repos/$repositoryFullName/code-scanning/analyses/$analysisId")
                .openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "GET"
            doInput = true
            doOutput = true

            setRequestProperty("Accept", "application/sarif+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Authorization", "Bearer $token")
        }

        handleExceptions(connection)

        val response = connection.inputStream.bufferedReader().readText()

        connection.disconnect()

        return response
    }
}
