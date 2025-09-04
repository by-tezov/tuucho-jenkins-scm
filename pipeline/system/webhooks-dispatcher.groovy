@Library('library@master') _

import com.tezov.jenkins.webhook.WebhookContent
import com.tezov.jenkins.webhook.enums.Key
import com.tezov.jenkins.webhook.enums.KeyPullRequest
import com.tezov.jenkins.webhook.enums.PullRequestAction
import com.tezov.jenkins.webhook.enums.Type

WebhookContent content = new WebhookContent()
Map<String, String> option = [:]

pipeline {
    agent none
    triggers {
        GenericTrigger(
                genericVariables: [
                        [key: 'payload', value: '$', regexpFilter: ''],
                        [key: 'payload_pullRequestAction', value: '$.action', defaultValue: 'not_a_pull_request', regexpFilter: '']
                ],
                genericHeaderVariables: [
                        [key: 'X-GitHub-Event', regexpFilter: '']
                ],
                causeString: '',
                token: 'WEBHOOKS-DISPATCHER',
                tokenCredentialId: '',
                silentResponse: true,
                printPostContent: false,
                printContributedVariables: false,
                shouldNotFlatten: false,
                regexpFilterText: '$payload_pullRequestAction',
                regexpFilterExpression: '^(opened|reopened|labeled|not_a_pull_request)$'
        )
    }

    options {
        parallelsAlwaysFailFast()
        ansiColor('xterm')
    }

    stages {
        stage('parse event payload') {
            parallel {
                stage('push') {
                    when {
                        expression { x_github_event == Type.push.value }
                    }
                    steps {
                        script {
                            addBadge(icon: 'symbol-git-pull-request-outline plugin-ionicons-api', text: 'push')
                            def jsonPayload = readJSON text: payload
                            content[Key.type] = Type.push.value
                            content[Key.repositoryName] = jsonPayload.repository.name

                            if (content[Key.repositoryName] == constant.repository.tuucho) {
                                addPlatformBadge(constant.platform.android)
                                addPlatformBadge(constant.platform.ios)
                            }

                            content[Key.sourceBranch] = jsonPayload.ref.replace('refs/heads/', '')
                            content[Key.isSourceBranchDeleted] = jsonPayload.deleted

                            if (!content[Key.isSourceBranchDeleted]) {
                                content[Key.author] = jsonPayload.head_commit.author.name
                                content[Key.commitMessage] = jsonPayload.head_commit.message
                                try {
                                    def pullRequestResponse = pullRequestResponse = getPullRequestData(
                                            content[Key.sourceBranch]
                                    )
                                    def pullRequest = [:]
                                    pullRequest[KeyPullRequest.sha] = pullRequestResponse.head.sha
                                    pullRequest[KeyPullRequest.targetBranch] = pullRequestResponse.base.ref
                                    pullRequest[KeyPullRequest.number] = pullRequestResponse.number
                                    pullRequest[KeyPullRequest.state] = pullRequestResponse.state
                                    pullRequest[KeyPullRequest.isDraft] = pullRequestResponse.draft
                                    pullRequest[KeyPullRequest.labels] = pullRequestResponse.labels.collect { it.name }
                                    content[Key.pullRequest] = pullRequest
                                } catch (Exception ignored) {
                                    log.info "No pull request found"
                                }
                            }
                            log.info "content: ${content}"
                            if (content[Key.isSourceBranchDeleted]) {
                                currentBuild.displayName = "#${env.BUILD_NUMBER}"
                                currentBuild.description = "${content[Key.sourceBranch]}"
                                currentBuild.description += "<br>${jsonPayload.pusher.name} branch deleted"
                            } else {
                                currentBuild.displayName = "#${env.BUILD_NUMBER}"
                                currentBuild.description = "${content[Key.author]} - ${content[Key.commitMessage]}<br>"
                                currentBuild.description += "${content[Key.sourceBranch]}"
                                if (content[KeyPullRequest.targetBranch]) {
                                    currentBuild.description += "<br>-> ${content[KeyPullRequest.targetBranch]}"
                                }
                            }
                        }
                    }
                }
                stage('pull request') {
                    when {
                        expression { x_github_event == Type.pull.value }
                    }
                    steps {
                        script {
                            addBadge(icon: 'symbol-push-outline plugin-ionicons-api', text: 'pull request')

                            def jsonPayload = readJSON text: payload
                            content[Key.type] = Type.pull.value
                            content[Key.repositoryName] = jsonPayload.repository.name

                            if (content[Key.repositoryName] == constant.repository.tuucho) {
                                addPlatformBadge(constant.platform.android)
                                addPlatformBadge(constant.platform.ios)
                            }

                            content[Key.sourceBranch] = jsonPayload.pull_request.head.ref

                            def pullRequest = [:]
                            pullRequest[KeyPullRequest.sha] = jsonPayload.pull_request.head.sha
                            pullRequest[KeyPullRequest.action] = jsonPayload.action
                            pullRequest[KeyPullRequest.targetBranch] = jsonPayload.pull_request.base.ref
                            pullRequest[KeyPullRequest.number] = jsonPayload.number
                            pullRequest[KeyPullRequest.state] = jsonPayload.pull_request.state
                            pullRequest[KeyPullRequest.isDraft] = jsonPayload.pull_request.draft
                            pullRequest[KeyPullRequest.labels] = jsonPayload.pull_request.labels.collect { it.name }
                            if (jsonPayload.label) {
                                def labelAdded = jsonPayload.label.name
                                pullRequest[KeyPullRequest.labelAdded] = labelAdded
                                def labels = pullRequest[KeyPullRequest.labels]
                                if (!labels.contains(labelAdded)) {
                                    labels << labelAdded
                                    pullRequest[KeyPullRequest.labels] = labels
                                }
                            }
                            content[Key.pullRequest] = pullRequest
                            def headCommit = getHeadCommitData(
                                    jsonPayload.pull_request.head.sha
                            )
                            content[Key.commitMessage] = headCommit.commit.message
                            content[Key.author] = headCommit.commit.author.name

                            log.info "content: ${content}"
                            currentBuild.displayName = "#${env.BUILD_NUMBER}"
                            currentBuild.description = "${content[Key.author]} - ${content[Key.commitMessage]}<br>"
                            currentBuild.description += "${content[Key.sourceBranch]}"
                            currentBuild.description += "<br>-> ${content[KeyPullRequest.targetBranch]}"
                        }
                    }
                }
            }
        }
        stage('parse message option') {
            when {
                expression { content[Key.commitMessage] }
            }
            steps {
                script {
                    // Search for ci://key1=value1&key2=value2
                    def ciPrefixPattern = ~/ci:\/\/(\w+=[^&\/\s]*(?:\/[^&\/\s]*)*(?:&\w+=[^&\/\s]*(?:\/[^&\/\s]*)*)*)?/
                    def matcher = content[Key.commitMessage] =~ ciPrefixPattern
                    if (matcher) {
                        def segments = matcher[0][1].split('&')
                        for (int i = 0; i < segments.length; i++) {
                            def parts = segments[i].split('=')
                            if (parts.length == 2) {
                                def key = parts[0]
                                def value = parts[1]
                                option[key] = value
                            }
                        }
                    }
                    log.info "option: ${option}"
                }
            }
        }

        stage('pull-request') {
            when {
                expression {
                    /* TUUCHO AN+IOS */
                    // not deleted
                    // and epic|feat|chore|fix|release
                    // and
                    //       push and has_pull_request and not draft
                    //   or
                    //       pull_request  and not draft and (opened | reopened)
                    //   or
                    //       label ('E2E Test' or 'Unit Test'')
                    matcher(content) {
                        and {
                            expression(Key.repositoryName) { it == constant.repository.tuucho }
                            expression(Key.isSourceBranchDeleted) { it == null || it == false }
                            regex(Key.sourceBranch, /(?:epic|feat|chore|fix|release)\/.*/)
                            or {
                                and {
                                    exact(Key.type, Type.push)
                                    expression(Key.pullRequest) { it != null }
                                    exact(KeyPullRequest.isDraft, false)
                                }
                                and {
                                    exact(Key.type, Type.pull)
                                    exact(KeyPullRequest.isDraft, false)
                                    or {
                                        exact(KeyPullRequest.action, PullRequestAction.opened)
                                        exact(KeyPullRequest.action, PullRequestAction.reopened)
                                    }
                                }
                                and {
                                    exact(Key.type, Type.pull)
                                    exact(KeyPullRequest.action, PullRequestAction.labeled)
                                    or {
                                        exact(KeyPullRequest.labelAdded, constant.label.e2eTestAN)
                                        exact(KeyPullRequest.labelAdded, constant.label.e2eTestIOS)
                                        exact(KeyPullRequest.labelAdded, constant.label.unitTest)
                                    }
                                }
                            }
                        }
                        log { message -> log.info message }
                    }
                }
            }
            steps {
                script {
                    def launchE2eTestAN = option[constant.commitOption.e2eTestAN]?.toBoolean()
                            ?: content[KeyPullRequest.labels]?.contains(constant.label.e2eTestAN)
                            ?: false
                    def launchE2ETestIOS = option[constant.commitOption.e2eTestIOS]?.toBoolean()
                            ?: content[KeyPullRequest.labels]?.contains(constant.label.e2eTestIOS)
                            ?: false
                    def e2eTestCreateVisualBaseline = option[constant.commitOption.e2eTestCreateVisualBaseline]?.toBoolean()
                            ?: content[KeyPullRequest.labels]?.contains(constant.label.e2eTestCreateVisualBaseline)
                            ?: false

                    log.info "Triggering pull-request for branch: ${content[Key.sourceBranch]}"
                    build job: 'pull-request', parameters: [
                            string(name: 'SOURCE_BRANCH', value: content[Key.sourceBranch]),
                            string(name: 'TARGET_BRANCH', value: content[KeyPullRequest.targetBranch]),
                            string(name: 'BUILD_TYPE', value: option[constant.commitOption.buildType] ?: constant.buildType.debug),
                            string(name: 'FLAVOR_TYPE', value: option[constant.commitOption.flavorType] ?: constant.flavorType.mock),
                            string(name: 'LANGUAGE', value: option[constant.commitOption.language] ?: constant.language.en),
                            string(name: 'BRANCH_NAME_QA', value: option[constant.commitOption.brancheNameQA] ?: 'master'),
                            booleanParam(name: 'E2E_TEST_CREATE_VISUAL_BASELINE', value: e2eTestCreateVisualBaseline),
                            booleanParam(name: 'E2E_TEST_AN', value: launchE2eTestAN),
                            string(name: 'DEVICE_NAME_AN', value: option[constant.commitOption.deviceAN] ?: ''),
                            booleanParam(name: 'E2E_TEST_IOS', value: launchE2ETestIOS),
                            string(name: 'DEVICE_NAME_IOS', value: option[constant.commitOption.deviceIOS] ?: ''),
                            string(name: 'COMMIT_AUTHOR', value: content[Key.author]),
                            string(name: 'COMMIT_MESSAGE', value: content[Key.commitMessage]),
                            string(name: 'CALLER_BUILD_NUMBER', value: env.BUILD_NUMBER),
                            string(name: 'PULL_REQUEST_SHA', value: content[KeyPullRequest.sha])
                    ], wait: false
                }
            }
        }
        /* TODO stage deploy */
    }
    post {
        failure {
            script {
                log.info payload
            }
        }
    }
}