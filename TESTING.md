# Manual Testing Guide for GitHub Authentication

## Overview
This guide provides steps to manually test the new GitHub authentication mechanism.

## Test Cases

### 1. GitHub.com Authentication
**Expected Behavior**: The plugin should use IntelliJ's built-in GitHub authentication automatically.

**Steps**:
1. Open IntelliJ IDEA
2. Open a project that's connected to a GitHub.com repository
3. Open the SARIF viewer tool window
4. The plugin should automatically attempt to authenticate using IntelliJ's GitHub integration
5. If not authenticated, you should see a dialog prompting you to configure GitHub authentication

**Verification**:
- No manual PAT configuration should be required for GitHub.com
- Error messages should direct users to IntelliJ's GitHub settings

### 2. GitHub Enterprise Server (GHES) Authentication
**Expected Behavior**: The plugin should continue to use PAT-based authentication for GHES instances.

**Steps**:
1. Configure a GHES hostname in Settings > Tools > SARIF Viewer
2. Add a valid PAT for the GHES instance
3. Open a project connected to the GHES repository
4. Open the SARIF viewer tool window
5. The plugin should use the configured PAT

**Verification**:
- PAT configuration should still be required for GHES
- Error messages should indicate missing PAT for GHES instances

### 3. Settings UI
**Expected Behavior**: The settings UI should reflect the new authentication approach.

**Steps**:
1. Go to Settings > Tools > SARIF Viewer
2. Check the GitHub.com section
3. Check the GHES section

**Verification**:
- GitHub.com section should show information about automatic authentication
- GHES section should still allow PAT configuration
- No GitHub.com PAT field should be present

## Error Scenarios

### 1. Missing GitHub.com Authentication
**Steps**: Use plugin with GitHub.com repo when not authenticated in IntelliJ
**Expected**: Clear error message directing to IntelliJ GitHub settings

### 2. Missing GHES PAT
**Steps**: Use plugin with GHES repo without configuring PAT
**Expected**: Clear error message about missing PAT for GHES

### 3. Invalid GHES PAT
**Steps**: Configure invalid PAT for GHES
**Expected**: Authentication failure with appropriate error message

## Backward Compatibility

### 1. Existing PAT Configurations
**Steps**: Test with existing plugin installations that have GitHub.com PATs configured
**Expected**: Plugin should work without requiring reconfiguration

### 2. GHES Workflows
**Steps**: Test existing GHES workflows
**Expected**: No change in behavior for GHES instances