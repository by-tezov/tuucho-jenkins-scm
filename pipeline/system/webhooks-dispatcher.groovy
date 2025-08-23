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

    environment {
        GITHUB_API_TOKEN = credentials('github-api-token')
    }

    options {
        parallelsAlwaysFailFast()
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
                            addPlatformBadge(constant.repositoryToPlatform[content[Key.repositoryName]])
                            content[Key.sourceBranch] = jsonPayload.ref.replace('refs/heads/', '')
                            content[Key.isSourceBranchDeleted] = jsonPayload.deleted

                            if (!content[Key.isSourceBranchDeleted]) {
                                content[Key.author] = jsonPayload.head_commit.author.name
                                content[Key.commitMessage] = jsonPayload.head_commit.message
                                def pullRequestResponse = getPullRequestData(
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
                                currentBuild.description += "<br>-> ${content[KeyPullRequest.targetBranch]}"
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
                            addPlatformBadge(constant.repositoryToPlatform[content[Key.repositoryName]])
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
                                def key = parts[0].toLowerCase()
                                def value = parts[1].toLowerCase()
                                option[key] = value
                            }
                        }
                    }
                    log.info "option: ${option}"
                }
            }
        }
        stage('dispatch pipeline') {
            parallel {
                stage('build') {
                    when {
                        expression {
                            // not deleted
                            // and epic|feat|chore|fix|release
                            // and
                            //       push and has_pull_request
                            //   or
                            //       pull_request and (opened | reopened)
                            //   or
                            //       label ('Test Auto' or 'Unit Test')
                            matcher(content) {
                                and {
                                    expression(Key.isSourceBranchDeleted) { it == null || it == false }
                                    regex(Key.sourceBranch, /(?:epic|feat|chore|fix|release)\/.*/)
                                    or {
                                        and {
                                            exact(Key.type, Type.push)
                                            exact(KeyPullRequest.isDraft, false)
                                            expression(Key.pullRequest) { it != null }
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
                                                exact(KeyPullRequest.labelAdded, constant.label.testAuto)
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
                            def launchTestAuto = option[constant.commitOption.testE2E]?.toBoolean()
                                    ?: content[KeyPullRequest.labels]?.contains(constant.label.testAuto)
                                    ?: false

                            /* ANDROID */
                            matcher(content) {
                                expression(Key.repositoryName) {
                                    constant.repositoryToPlatform[it] == constant.platform.android
                                }
                                log { message -> log.info message }
                                onSuccess {
                                    log.info "Triggering android/build for branch: ${content[Key.sourceBranch]}"
                                    build job: 'android/build', parameters: [
                                            string(name: 'PULL_REQUEST_SHA', value: content[KeyPullRequest.sha]),
                                            string(name: 'SOURCE_BRANCH', value: content[Key.sourceBranch]),
                                            string(name: 'TARGET_BRANCH', value: content[KeyPullRequest.targetBranch]),
                                            string(name: 'BUILD_TYPE', value: option[constant.commitOption.buildType] ?: constant.buildType.debug),
                                            string(name: 'FLAVOR_TYPE', value: option[constant.commitOption.flavorType] ?: constant.flavorType.mock),
                                            string(name: 'LANGUAGE', value: option[constant.commitOption.language] ?: constant.language.en),
                                            string(name: 'BRANCH_NAME_QA', value: option[constant.commitOption.brancheNameQA] ?: 'chore/migration-tuucho'),
                                            booleanParam(name: 'TEST_E2E', value: launchTestAuto),
                                            booleanParam(name: 'TEST_E2E_WAIT_TO_SUCCEED', value: option[constant.commitOption.testE2EWaitToSucceed]?.toBoolean() ?: false),
                                            string(name: 'COMMIT_AUTHOR', value: content[Key.author]),
                                            string(name: 'COMMIT_MESSAGE', value: content[Key.commitMessage]),
                                    ], wait: false
                                }
                            }
                        }
                    }
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