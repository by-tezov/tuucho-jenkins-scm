@Library('library@master') _

def setStatus = { status, message ->
    setPullRequestStatus(
            params.PULL_REQUEST_SHA,
            constant.pullRequestContextStatus.danger,
            status,
            "${message}"
    )
}

pipeline {
    agent {
        node {
            label 'android-danger'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        separator(name: '-build-')
        string(name: 'SOURCE_BRANCH', defaultValue: '', description: 'Source branch to build')
        string(name: 'TARGET_BRANCH', defaultValue: '', description: 'Target branch to merge (merge is done only locally, not on remote)')
        separator(name: '-system-', sectionHeader: '-system-')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
        string(name: 'CALLER_BUILD_NUMBER', defaultValue: '', description: 'Caller build number')
        string(name: 'PULL_REQUEST_NUMBER', defaultValue: '', description: 'Pull request number')
        string(name: 'PULL_REQUEST_SHA', defaultValue: '', description: 'Pull request sha (used to update status on GitHub)')
    }

    environment {
        AGENT = 'android-danger'
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
                                currentBuild.displayName = "#${env.BUILD_NUMBER}-#${CALLER_BUILD_NUMBER}"
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
                                        "Danger job initiated"
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

        stage('clone') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                dir('project') {
                    script {
                        setStatus(
                                constant.pullRequestStatus.pending,
                                "Cloning"
                        )
                        clone(params.SOURCE_BRANCH, params.TARGET_BRANCH, false)
                    }
                }
            }
        }

        stage('ktlint validation') {
            options {
                timeout(time: 2, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "KtLint validating"
                    )
                    runGradleTask('project/tuucho', 'rootKtLintReport')
//                    repository.storeReport('project/tuucho/build/reports/ktlint')
//                    currentBuild.description += """<br><a href="http://localhost/jenkins/tuucho-report/${repository.relativePath()}/tuucho/build/reports/ktlint/index.html" target="_blank">KtLint</a>"""
                }
            }
        }

        stage('detekt validation') {
            options {
                timeout(time: 2, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Detekt validating"
                    )
                    runGradleTask('project/tuucho', 'rootDetektReport')
                    repository.storeReport('project/tuucho/build/reports/detekt')
                    currentBuild.description += """<br><a href="http://localhost/jenkins/tuucho-report/${repository.relativePath()}/tuucho/build/reports/detekt/detekt-aggregated.html" target="_blank">Detekt</a>"""
                }
            }
        }

        stage('danger report') {
            options {
                timeout(time: 2, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Danger reporting"
                    )
                    withCredentials([string(credentialsId: env.GITHUB_API_TOKEN_ID, variable: 'GITHUB_TOKEN')]) {
                        withEnv([
                                "DANGER_GITHUB_API_TOKEN=${GITHUB_TOKEN}",
                                "CHANGE_ID=${params.PULL_REQUEST_NUMBER}",
                                "CHANGE_URL=https://github.com/${env.GITHUB_ORGANIZATION}/${env.GITHUB_TUUCHO}/pull/${params.PULL_REQUEST_NUMBER}",
                        ]) {
                            dir('project/tuucho') {
                                sh """
                                    danger-kotlin ci \
                                        --dangerfile .danger.df.kts \
                                        --verbose \
                                        --base ${params.TARGET_BRANCH}
                                """
                            }
                        }
                    }
                }
            }
        }
    }
    post {
        success {
            script {
                setStatus(
                        constant.pullRequestStatus.success,
                        "Succeed"
                )
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
