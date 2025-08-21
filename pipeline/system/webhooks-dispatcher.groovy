@Library('library@master') _

import com.tezov.jenkins.webhook.WebhookContent
import com.tezov.jenkins.webhook.enums.Key
import com.tezov.jenkins.webhook.enums.KeyPullRequest
import com.tezov.jenkins.webhook.enums.PullRequestAction
import com.tezov.jenkins.webhook.enums.Type
import com.tezov.jenkins.webhook.enums.RepositoryName

WebhookContent content = new WebhookContent()
Map<String, String> option = [:]

pipeline {
    agent none
    triggers {
        GenericTrigger(
                genericVariables: [
                        [key: 'payload', value: '$', regexpFilter: ''],
                        [key: 'payload_pullRequestAction', value: '$.object_attributes.action', defaultValue: 'not_a_pull_request' , regexpFilter: ''],
                        [key: 'payload_pullRequestLabelChanged', value: '$.changes.labels', defaultValue: 'no_labels_changed' , regexpFilter: '']
                ],
                genericHeaderVariables: [
                        [key: 'X-Gitlab-Event', regexpFilter: ''],
                        [key: 'X-Gitlab-Token', regexpFilter: '']
                ],
                causeString: '',
                token: 'WEBHOOKS-DISPATCHER',
                tokenCredentialId: '',
                silentResponse: true,
                printPostContent: false,
                printContributedVariables: false,
                shouldNotFlatten: false,
                // filter event to avoid duplicate job launch
                // - on action review_requested we trigger as many as there are user, so we accept only open/reopen and anything not a pull request
                // - on push, an update action on pull request is sent, we ignore it and let pass only the push event but we accept update with labels changed
                regexpFilterText: '$payload_pullRequestAction $payload_pullRequestLabelChanged',
                regexpFilterExpression: '^(((open|reopen|not_a_pull_request) .*))|(update (?!no_labels_changed$).+)$'
        )
    }

    environment {
        WEBHOOK_SECRET = credentials('webhook-secret')
        GIT_API_TOKEN = credentials('git-api-token')
    }

    options {
        parallelsAlwaysFailFast()
    }

    stages {
        stage('check token') {
            steps {
                script {
                    if (x_gitlab_token != WEBHOOK_SECRET) {
                        error("x_gitlab_token does not match WEBHOOK_SECRET")
                    } else {
                        log.info "x_gitlab_token matches WEBHOOK_SECRET"
                    }
                }
            }
        }

        stage('parse event payload') {
            parallel {
               stage('push') {
                    when {
                        expression { x_gitlab_event == Type.push.value }
                    }
                    steps {
                        script {
                            def jsonPayload = readJSON text: payload
                            addBadge(icon: 'symbol-git-pull-request-outline plugin-ionicons-api', text: 'push')

                            content[Key.type] = Type.push.value
                            content[Key.repositoryName] = jsonPayload.repository.name

                            addPlatformBadge(RepositoryName.from(content[Key.repositoryName]).name())

                            content[Key.sourceBranch] = jsonPayload.ref.replace('refs/heads/', '')
                            content[Key.isSourceBranchDeleted] = jsonPayload.deleted

                            if (!content[Key.isSourceBranchDeleted]) {
                                content[Key.author] = jsonPayload.commits[0].author.name
                                content[Key.commitMessage] = jsonPayload.commits[0].message

                                // retrieve pull requests associated to source branch
                                def encodedProjectPath = URLEncoder.encode(jsonPayload.project.path_with_namespace, "UTF-8")
                                def encodedSourceBranch = URLEncoder.encode(content[Key.sourceBranch], "UTF-8")
                                def response = httpRequest(
                                        url: "http://gitlab:80/gitlab/api/v4/projects/${encodedProjectPath}/merge_requests?state=opened&source_branch=${encodedSourceBranch}",
                                        timeout : env.GIT_API_REQUEST_TIMEOUT.toInteger(),
                                        httpMode: 'GET',
                                        customHeaders: [
                                                [name: 'Accept', value: "application/json"],
                                                [name: 'PRIVATE-TOKEN', value: "${GIT_API_TOKEN}"],
                                        ],
                                        validResponseCodes: '200'
                                )

                                try {
                                    def jsonResponseContent = readJSON text: response.content
                                    def pullRequests = []
                                    jsonResponseContent.each { responseContent ->
                                        def pullRequest = [:]
                                        pullRequest[KeyPullRequest.targetBranch] = responseContent.target_branch
                                        pullRequest[KeyPullRequest.number] = responseContent.id
                                        pullRequest[KeyPullRequest.state] = responseContent.state
                                        pullRequest[KeyPullRequest.isDraft] = responseContent.draft
                                        pullRequest[KeyPullRequest.labels] = responseContent.labels
                                        pullRequests.add(pullRequest)
                                    }
                                    if(pullRequests.size() == 1) {
                                        content[Key.pullRequest] = pullRequests[0]
                                    }
                                    else if(pullRequests.size() > 1) {
                                        content[Key.pullRequest] = pullRequests
                                    }
                                }
                                catch (error) {
                                    log.error error
                                    log.info response.content
                                }
                            }
                            log.info "content: ${content}"

                            if(content[Key.isSourceBranchDeleted]) {
                                currentBuild.displayName = "#${env.BUILD_NUMBER}-${content[Key.sourceBranch]}"
                                currentBuild.description = "${jsonPayload.pusher.name} branch deleted"
                            }
                            else {
                                currentBuild.displayName = "#${env.BUILD_NUMBER}-${content[Key.sourceBranch]}"
                                currentBuild.description = "${content[Key.author]}\n${content[Key.commitMessage]}"
                            }
                        }
                    }
               }
               stage('pull request') {
                    when {
                        expression { x_gitlab_event == Type.pull.value }
                    }
                    steps {
                        script {
                            def jsonPayload = readJSON text: payload
                            addBadge(icon: 'symbol-push-outline plugin-ionicons-api', text: 'pull request')

                            content[Key.type] = Type.pull.value
                            content[Key.repositoryName] = jsonPayload.repository.name

                            addPlatformBadge(RepositoryName.from(content[Key.repositoryName]).name())

                            content[Key.sourceBranch] = jsonPayload.object_attributes.source_branch

                            def pullRequest = [:]
                            pullRequest[KeyPullRequest.action] = jsonPayload.object_attributes.action
                            pullRequest[KeyPullRequest.targetBranch] = jsonPayload.object_attributes.target_branch
                            pullRequest[KeyPullRequest.number] = jsonPayload.object_attributes.id
                            pullRequest[KeyPullRequest.state] = jsonPayload.object_attributes.state
                            pullRequest[KeyPullRequest.isDraft] = jsonPayload.object_attributes.draft
                            if(jsonPayload.changes.labels?.current) {
                                if(pullRequest[KeyPullRequest.action] == PullRequestAction.update.value) {
                                    pullRequest[KeyPullRequest.action] = PullRequestAction.labeled.value
                                    def previousLabels = jsonPayload.changes.labels?.previous?.collect { it.title } ?: []
                                    pullRequest[KeyPullRequest.labelsAdded] = jsonPayload.changes.labels.current
                                            .findAll { !(it.title in previousLabels) }
                                            .collect { it.title }
                                }
                                pullRequest[KeyPullRequest.labels] = jsonPayload.changes.labels.current
                                        .collect { it.title } as Set
                            }
                            content[Key.pullRequest] = pullRequest
                            content[Key.commitMessage] = jsonPayload.object_attributes.last_commit.message
                            content[Key.author] = jsonPayload.object_attributes.last_commit.author.name
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
                                            expression(Key.pullRequest) { it != null }
                                        }
                                        and {
                                            exact(Key.type, Type.pull)
                                            or {
                                                exact(KeyPullRequest.action, PullRequestAction.opened)
                                                exact(KeyPullRequest.action, PullRequestAction.reopened)
                                            }
                                        }
                                        and {
                                            exact(Key.type, Type.pull)
                                            exact(KeyPullRequest.action, PullRequestAction.labeled)
                                            contains(KeyPullRequest.labelsAdded, 'Test Auto')
                                        }
                                    }
                                }
                                log { message -> log.info message }
                            }
                        }
                    }
                    steps {
                        script {
                            def launchTestAuto = option['test_e2e']?.toBoolean()
                                    ?: content[KeyPullRequest.labelsAdded]?.contains('Test Auto')
                                    ?: content[KeyPullRequest.labels]?.contains('Test Auto')
                                    ?: false

                            /* ANDROID */
                            matcher(content) {
                                exact(Key.repositoryName, RepositoryName.android)
                                log { message -> log.info message }
                                onSuccess {
                                    log.info "Triggering android/build for branch: ${content[Key.sourceBranch]}"
                                    build job: 'android/build', parameters: [
                                            string(name: 'BRANCH_NAME', value: content[Key.sourceBranch]),
                                            string(name: 'ENVIRONMENT', value: option['environment'] ?: 'debug'),
                                            string(name: 'LANGUAGE', value: option['language'] ?: 'fr'),
                                            string(name: 'BRANCH_NAME_QA', value: option['branch_name_qa'] ?: 'master'),
                                            string(name: 'DEVICE', value: option['device'] ?: ''),
                                            booleanParam(name: 'TEST_E2E', value: launchTestAuto),
                                            booleanParam(name: 'TEST_E2E_WAIT_TO_SUCCEED', value: option['test_e2e_wait_to_succeed']?.toBoolean() ?: launchTestAuto),
                                            string(name: 'COMMIT_AUTHOR', value: content[Key.author]),
                                            string(name: 'COMMIT_MESSAGE', value: content[Key.commitMessage]),
                                    ], wait: false
                                }
                            }

                            /* IOS */
                            matcher(content) {
                                exact(Key.repositoryName, RepositoryName.ios)
                                log { message -> log.info message }
                                onSuccess {
                                    log.info "Triggering ios/build job for branch: ${content[Key.sourceBranch]}"
                                    build job: 'ios/build', parameters: [
                                            string(name: 'ENVIRONMENT', value: option['environment'] ?: 'debug'),
                                            string(name: 'BRANCH_NAME', value: content[Key.sourceBranch]),
                                            string(name: 'LANGUAGE', value: option['language'] ?: 'fr'),
                                            string(name: 'BRANCH_NAME_QA', value: option['branch_name_qa'] ?: 'master'),
                                            string(name: 'DEVICE', value: option['device'] ?: ''),
                                            booleanParam(name: 'TEST_E2E', value: launchTestAuto),
                                            booleanParam(name: 'TEST_E2E_WAIT_TO_SUCCEED', value: option['test_e2e_wait_to_succeed']?.toBoolean() ?: launchTestAuto),
                                            string(name: 'COMMIT_AUTHOR', value: content[Key.author]),
                                            string(name: 'COMMIT_MESSAGE', value: content[Key.commitMessage]),
                                    ], wait: false
                                }
                            }
                        }
                    }
                }
//                stage('deploy') {
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
//                                exact(Key.repositoryName, RepositoryName.android)
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
//
//                            /* IOS */
//                            matcher(content) {
//                                exact(Key.repositoryName, RepositoryName.ios)
//                                log { message -> log.info message }
//                                onSuccess {
//                                    log.info "Triggering ios/deploy for branch: ${content[Key.sourceBranch]}"
//                                    build job: 'ios/deploy', parameters: [
//                                            string(name: 'BRANCH_NAME', value: content[Key.sourceBranch]),
//                                            string(name: 'RELEASE_NOTE', value: option['release_note'] ?: ''),
//                                            string(name: 'ENVIRONMENT', value: environment),
//                                            string(name: 'BUNDLE_VERSION', value: option['bundle_version'] ?: ''),
//                                            string(name: 'MARKETING_VERSION', value: option['marketing_version'] ?: ''),
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