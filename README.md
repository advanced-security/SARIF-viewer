# SARIF-viewer

<!-- Plugin description -->

SARIF viewer to view the results of static analysis tools in the IDE.
The Sarif comes from GitHub Advanced Security (GHAS) and is a standard for static analysis results.

<!-- Plugin description end -->

## Installation

### Manual

- Download the signed zip file release from GitHub Releases : https://github.com/adrienpessu/SARIF-viewer/releases
- Add it to you IDE via `Settings > Plugins > Install Plugin from Disk...`

## Configuration

You must provide a personal access token (PAT) to access the GitHub API with as least the following scopes:
- Pull request read
- Code scanning read
- Metadata read

And add it to the plugin configuration via `Settings > Tools > Sarif Viewer`

## Usage

If there is a scan done one the current branch, the plugin will automatically display the results in the tool window.
You can change the current branch to see the results of another branch.
You can also use the select box to select the results of a specific PR.
