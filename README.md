# SARIF-viewer

![Version](https://img.shields.io/jetbrains/plugin/v/23159-sarif-viewer) ![example branch parameter](https://github.com/advanced-security/SARIF-viewer/actions/workflows/build.yml/badge.svg?branch=main)


<img alt="docs/vuln_tree.png" width="640" src="docs/vuln_tree.png"/>

<!-- Plugin description -->

SARIF viewer to view the results of static analysis tools in the IDE.
The Sarif comes from GitHub Advanced Security (GHAS) or from the local file system.

You must provide in the settings a personal access token (PAT) to access the GitHub API with as least the following scopes:
- Pull request read
- Code scanning read
- Metadata read


<!-- Plugin description end -->

## Installation

### Manual

- Download the signed zip file release from GitHub Releases : https://github.com/advanced-security/SARIF-viewer/releases
- Add it to you IDE via `Settings > Plugins > Install Plugin from Disk...`

## Configuration

You must provide a personal access token (PAT) to access the GitHub API with as least the following scopes:
- Pull request read
- Code scanning read
- Metadata read

And add it to the plugin configuration via `Settings > Tools > Sarif Viewer`

If you are using GHES, you must also provide the URL and the corresponding token of your GHES instance.

<img alt="docs/settings.png" width="640" src="docs/settings.png"/>

## Usage

If there is a scan done one the current branch, the plugin will automatically display the results in the tool window.

When you change branch, the plugin will automatically display the results of the new branch.
If the current branch has one or more pull request, you will be able to select with the combobox the PR to display the results of.

The result will be grouped by vulnerabilities and you will be able to navigate to the source code by clicking on the result. Also a detail will also be displayed with the path of the vulnerability and the description to help you remediate.

## 🤝&nbsp; Found a bug? Missing a specific feature?

Feel free to **file a new issue** with a respective [title and description](https://github.com/advanced-security/SARIF-viewer/issues) repository. If you already found a solution to your problem, **we would love to review your pull request**!

## License 

This project is licensed under the terms of the MIT open source license. Please refer to [MIT](./LICENSE.txt) for the full terms.
