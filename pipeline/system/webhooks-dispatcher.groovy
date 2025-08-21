@Library('library@chore/migrate-to-tuucho') _

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
                                // retrieve pull requests associated to source branch
                                def response = httpRequest(
                                        url: "https://api.github.com/repos/${jsonPayload.repository.full_name}/pulls?head=${jsonPayload.repository.organization}:${content[Key.sourceBranch]}",
                                        timeout: env.GITHUB_API_REQUEST_TIMEOUT.toInteger(),
                                        httpMode: 'GET',
                                        customHeaders: [
                                                [name: 'Accept', value: "application/json"],
                                                [name: 'Authorization', value: "Bearer ${GITHUB_API_TOKEN}"]
                                        ],
                                        validResponseCodes: '200'
                                )
                                try {
                                    def jsonResponseContent = readJSON text: response.content
                                    def pullRequests = []
                                    jsonResponseContent.each { responseContent ->
                                        def pullRequest = [:]
                                        pullRequest[KeyPullRequest.targetBranch] = responseContent.base.ref
                                        pullRequest[KeyPullRequest.number] = responseContent.number
                                        pullRequest[KeyPullRequest.state] = responseContent.state
                                        pullRequest[KeyPullRequest.isDraft] = responseContent.draft
                                        pullRequest[KeyPullRequest.labels] = responseContent.labels.collect { it.name }
                                        pullRequests.add(pullRequest)
                                    }
                                    if (pullRequests.size() == 1) {
                                        content[Key.pullRequest] = pullRequests[0]
                                    } else if (pullRequests.size() > 1) {
                                        content[Key.pullRequest] = pullRequests
                                    }
                                }
                                catch (error) {
                                    log.error error
                                    log.info response.content
                                }
                            }
                            log.info "content: ${content}"

                            if (content[Key.isSourceBranchDeleted]) {
                                currentBuild.displayName = "#${env.BUILD_NUMBER}-${content[Key.sourceBranch]}"
                                currentBuild.description = "${jsonPayload.pusher.name} branch deleted"
                            } else {
                                currentBuild.displayName = "#${env.BUILD_NUMBER}-${content[Key.sourceBranch]}"
                                currentBuild.description = "${content[Key.author]}\n${content[Key.commitMessage]}"
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
                            // retrieve last source branch commit
                            def response = httpRequest(
                                    url: "https://api.github.com/repos/${jsonPayload.repository.full_name}/commits?sha=${jsonPayload.pull_request.head.sha}&per_page=1&page=1",
                                    timeout: env.GITHUB_API_REQUEST_TIMEOUT.toInteger(),
                                    httpMode: 'GET',
                                    customHeaders: [
                                            [name: 'Accept', value: "application/json"],
                                            [name: 'Authorization', value: "Bearer ${GITHUB_API_TOKEN}"]
                                    ],
                                    validResponseCodes: '200'
                            )
                            try {
                                def jsonResponseContent = readJSON text: response.content
                                def headCommit = jsonResponseContent[0]
                                content[Key.commitMessage] = headCommit.commit.message
                                content[Key.author] = headCommit.commit.author.name
                            }
                            catch (error) {
                                log.error error
                                log.info response.content
                            }
                            log.info "content: ${content}"
                            currentBuild.displayName = "#${env.BUILD_NUMBER}-${content[Key.sourceBranch]}"
                            currentBuild.description = "${content[Key.author]} - ${content[Key.commitMessage]}"
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
                            //       label 'Test Auto'
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
                                            exact(KeyPullRequest.labelAdded, constant.label.testAuto)
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
                                            string(name: 'SOURCE_BRANCH', value: content[Key.sourceBranch]),
                                            string(name: 'TARGET_BRANCH', value: content[Key.targetBranch]),
                                            string(name: 'BUILD_TYPE', value: option[constant.commitOption.buildType] ?: constant.buildType.debug),
                                            string(name: 'FLAVOR_TYPE', value: option[constant.commitOption.flavorType] ?: constant.flavorType.mock),
                                            string(name: 'LANGUAGE', value: option[constant.commitOption.language] ?: constant.language.en),
                                            string(name: 'BRANCH_NAME_QA', value: option[constant.commitOption.brancheNameQA] ?: 'master'),
                                            string(name: 'DEVICE', value: option[constant.commitOption.device] ?: ''),
                                            booleanParam(name: 'TEST_E2E', value: launchTestAuto),
                                            booleanParam(name: 'TEST_E2E_WAIT_TO_SUCCEED', value: option[constant.commitOption.testE2EWaitToSucceed]?.toBoolean() ?: launchTestAuto),
                                            string(name: 'COMMIT_AUTHOR', value: content[Key.author]),
                                            string(name: 'COMMIT_MESSAGE', value: content[Key.commitMessage]),
                                    ], wait: false
                                }
                            }
                        }
                    }
                }
//                stage('deploy') { //TODO
//                    when {
//                        expression {
//                            // not deleted
//                            // and pull_request and labeled
//                            // and
//                            //       release and label All
//                            //   or
//                            //       epic|feat|chore|fix and label Debug
//                            matcher(content) {
//                                and {
//                                    expression(Key.isSourceBranchDeleted) { it == null || it == false }
//                                    exact(Key.type, Type.pull)
//                                    exact(KeyPullRequest.action, PullRequestAction.labeled)
//                                    or {
//                                        and {
//                                            regex(Key.sourceBranch, /release\/.*/)
//                                            or {
//                                                contains(KeyPullRequest.labelsAdded, 'Debug deploy')
//                                                contains(KeyPullRequest.labelsAdded, 'Production deploy')
//                                            }
//                                        }
//                                        and {
//                                            regex(Key.sourceBranch, /(?:epic|feat|chore|fix)\/.*/)
//                                            or {
//                                                contains(KeyPullRequest.labelsAdded, 'Debug deploy')
//                                            }
//                                        }
//                                    }
//                                }
//                                log { message -> log.info message }
//                            }
//                        }
//                    }
//                    steps {
//                        script {
//                            def environment = option['environment'] ?: {
//                                //switch (content[KeyPullRequest.label]) {
//                                    case 'Debug deploy': return 'debug'
//                                    //case production not done on purpose
//                                    default: return 'debug'
//                                }
//                            }()
//
//                            /* ANDROID */
//                            matcher(content) {
//                                exact(Key.repositoryName, RepositoryName.android) //TODO
//                                log { message -> log.info message }
//                                onSuccess {
//                                    log.info "Triggering android/deploy for branch: ${content[Key.sourceBranch]}"
//                                    build job: 'android/deploy', parameters: [
//                                            string(name: 'BRANCH_NAME', value: content[Key.sourceBranch]),
//                                            string(name: 'RELEASE_NOTE', value: option['release_note'] ?: "deploy ${content[Key.sourceBranch]}"),
//                                            string(name: 'ENVIRONMENT', value: environment),
//                                            string(name: 'MARKETING_VERSION', value: option['marketing_version'] ?: ''),
//                                            string(name: 'BUNDLE_VERSION', value: option['bundle_version'] ?: ''),
//                                            //booleanParam(name: 'DRY_RUN', value: option['dry_run']?.toBoolean() ?: false),
//                                            booleanParam(name: 'DRY_RUN', value: option['dry_run']?.toBoolean() ?: true), // TODO: for now, deploy is just a dry run
//                                            string(name: 'COMMIT_AUTHOR', value: content[Key.author]),
//                                            string(name: 'COMMIT_MESSAGE', value: content[Key.commitMessage]),
//                                    ], wait: false
//                                }
//                            }
//                        }
//                    }
//                }
            }
        }
    }
    post {
        failure {
            script {
                log.info payload
            }
        }
    }
}