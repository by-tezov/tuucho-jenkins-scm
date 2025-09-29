@Library('library@chore/update-jenkins-with-properties-files') _

def setStatus = { status, message ->
    setPullRequestStatus(
            params.PULL_REQUEST_SHA,
            constant.pullRequestContextStatus.maven,
            status,
            "${message}"
    )
}

String deploymentId = null

pipeline {
    agent {
        node {
//            label 'ios-publication'
            label 'ios-builder'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        separator(name: '-build-', sectionHeader: '-build-')
        string(name: 'SOURCE_BRANCH', defaultValue: 'release/0.0.1-alpha12', description: 'Source branch to build')
        string(name: 'TARGET_BRANCH', defaultValue: 'master', description: 'Target branch to merge into')
        booleanParam(name: 'UPLOAD', defaultValue: true, description: 'Upload to Maven Central')
        booleanParam(name: 'RELEASE', defaultValue: false, description: 'Release the publication')
        separator(name: '-system-', sectionHeader: '-system-')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
        string(name: 'CALLER_BUILD_NUMBER', defaultValue: '', description: 'Caller build number')
        string(name: 'PULL_REQUEST_SHA', defaultValue: '', description: 'Pull request sha (used to update status on GitHub)')
    }

    environment {
        AGENT = 'ios-builder'
    }

    options {
        parallelsAlwaysFailFast()
        ansiColor('xterm')
    }

    stages {
        stage('init') {
            steps {
                script {
                    parallel(
                            'update description': {
                                log.success "sourceBranch: ${params.SOURCE_BRANCH}, targetBranch: ${params.TARGET_BRANCH}"
                                currentBuild.displayName = "#${env.BUILD_NUMBER}-#${params.CALLER_BUILD_NUMBER}"
                                if (params.COMMIT_AUTHOR != '' && params.COMMIT_MESSAGE != '') {
                                    log.info "author: ${params.COMMIT_AUTHOR}, message: ${params.COMMIT_MESSAGE}"
                                    currentBuild.description = "${params.COMMIT_AUTHOR}<br>"
                                } else {
                                    currentBuild.description = ''
                                }
                                currentBuild.description += "${params.SOURCE_BRANCH}"
                                currentBuild.description += "<br>-> ${params.TARGET_BRANCH}"
                            },
                            'status pending': {
                                setStatus(
                                        constant.pullRequestStatus.pending,
                                        "Publishing job initiated"
                                )
                            },
                            'clean workspaces': {
                                workspace.clean()
                            },
                            'prepare variables': {
                                // send to upstream
                                env.AGENT = env.AGENT
                                env.JOB_NAME = env.JOB_NAME
                                env.BUILD_NUMBER = env.BUILD_NUMBER
                            })
                }
            }
        }

        // TODO look for PR release, take the next one after last tag // remove branch source and target

        stage('clone and merge') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                dir('project') {
                    script {
                        setStatus(
                                constant.pullRequestStatus.pending,
                                "Cloning and Merging: source: ${params.SOURCE_BRANCH} -> target:${params.TARGET_BRANCH}"
                        )
                        cloneAndMerge(params.SOURCE_BRANCH, params.TARGET_BRANCH)
                    }
                }
            }
        }

        stage('Publishing lib') {
            options {
                timeout(time: 10, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Publishing lib"
                    )
                    if (getIsSnapshot() == 'true') {
                        error("❌ SNAPSHOT version found in catalog lib — release build cannot continue")
                    }
                    withCredentials([
                            file(credentialsId: env.MAVEN_SIGNING_KEY, variable: 'MAVEN_SIGNING_KEY_FILE'),
                            string(credentialsId: env.MAVEN_SIGNING_PASSWORD, variable: 'MAVEN_SIGNING_PASSWORD'),
                    ]) {
                        withEnv(["MAVEN_SIGNING_KEY=" + readFile(MAVEN_SIGNING_KEY_FILE)]) {
                            sourceEnv {
                                runGradleTask("rootPublishProdToMavenLocal")
                            }
                        }
                    }
                }
            }
        }

        stage('Upload to Maven Central') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            when {
                expression { params.UPLOAD }
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Upload lib"
                    )
                    dir('project/.m2') {
                        def fileName = "tuucho-0.0.1-alpha13" //TODO retrieve version from catalog
                        sh "zip -r ${fileName}.zip ."
                        withCredentials([
                                string(credentialsId: env.MAVEN_AUTHORIZATION_TOKEN, variable: 'MAVEN_AUTHORIZATION_TOKEN'),
                        ]) {
                            def response = httpRequest(
                                    httpMode: 'POST',
                                    url: 'https://central.sonatype.com/api/v1/publisher/upload',
                                    multipartName: 'bundle',
                                    uploadFile: "${fileName}.zip",
                                    customHeaders: [
                                            [name: 'User-Agent', value: 'Jenkins'],
                                            [name: 'Authorization', value: "Bearer $MAVEN_AUTHORIZATION_TOKEN"]
                                    ],
                                    validResponseCodes: '201'
                            )
                            deploymentId = response.content
                            log.info "Deployment ID: ${response.content}"

                        }
                    }
                }
            }
        }

        stage('Release publication') {
            options {
                timeout(time: 1, unit: 'MINUTES')
            }
            when {
                expression { params.RELEASE && deploymentId != null }
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Release publication"
                    )
                    withCredentials([
                            string(credentialsId: env.MAVEN_AUTHORIZATION_TOKEN, variable: 'MAVEN_AUTHORIZATION_TOKEN'),
                    ]) {
                        // TODO: Check the status Validated first (can take some times, up to 5 mn)

                        httpRequest(
                                httpMode: 'POST',
                                url: "https://central.sonatype.com/api/v1/publisher/deployment/${deploymentId}",
                                customHeaders: [
                                        [name: 'User-Agent', value: 'Jenkins'],
                                        [name: 'Authorization', value: "Bearer $MAVEN_AUTHORIZATION_TOKEN"]
                                ],
                                validResponseCodes: '204'
                        )
                    }
                }
            }
        }

        // TODO wait deployment success, then merge the release PR on master + tag + github release ?
    }

    post {
        success {
            script {
                if (params.RELEASE && deploymentId != null) {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Succeed: Wait for PUBLISHED state"
                    )
                } else if (params.UPLOAD) {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Succeed: Need to manually publish"
                    )
                }  else {
                    setStatus(
                            constant.pullRequestStatus.success,
                            "Dry run Succeed"
                    )
                }
            }
        }
        failure {
            script {
                setStatus(
                        constant.pullRequestStatus.failure,
                        "Failed: Mm, I can't help you, but maybe the logs will... :"
                )
            }
        }
    }
}
