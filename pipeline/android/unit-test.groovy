@Library('library@master') _

def setStatus = { status, message ->
    setPullRequestStatus(
            params.PULL_REQUEST_SHA,
            constant.pullRequestContextStatus.unit_test,
            status,
            "${message}"
    )
}

pipeline {
    agent {
        node {
            label 'android-builder'
            customWorkspace "workspace/${env.JOB_NAME}/_${env.BUILD_NUMBER}"
        }
    }

    parameters {
        separator(name: '-build-')
        string(name: 'SOURCE_BRANCH', defaultValue: '', description: 'Source branch to build')
        string(name: 'TARGET_BRANCH', defaultValue: '', description: 'Target branch to merge (merge is done only locally, not on remote)')
        choice(name: 'BUILD_TYPE', choices: ['debug', 'release'], description: 'Build type')
        choice(name: 'FLAVOR_TYPE', choices: ['mock', 'prod'], description: 'Flavor type')
        separator(name: '-system-', sectionHeader: '-system-')
        string(name: 'COMMIT_AUTHOR', defaultValue: '', description: 'Commit author')
        string(name: 'COMMIT_MESSAGE', defaultValue: '', description: 'Commit message')
        string(name: 'CALLER_BUILD_NUMBER', defaultValue: '', description: 'Caller build number')
        string(name: 'PULL_REQUEST_SHA', defaultValue: '', description: 'Pull request sha (used to update status on GitHub)')
    }

    environment {
        AGENT = 'android-builder'
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
                                log.success "buildType: ${params.BUILD_TYPE}, falvorType: ${params.FLAVOR_TYPE}, sourceBranch: ${params.SOURCE_BRANCH}, targetBranch: ${params.TARGET_BRANCH}"
                                addBuildTypeBadge(params.BUILD_TYPE)
                                addFlavorTypeBadge(params.FLAVOR_TYPE)
                                currentBuild.displayName = "#${env.BUILD_NUMBER}-#${CALLER_BUILD_NUMBER}"
                                if (params.COMMIT_AUTHOR != '' && params.COMMIT_MESSAGE != '') {
                                    log.info "author: ${params.COMMIT_AUTHOR}, message: ${params.COMMIT_MESSAGE}"
                                    currentBuild.description = "${params.COMMIT_AUTHOR} - ${params.COMMIT_MESSAGE}<br>"
                                } else {
                                    currentBuild.description = ''
                                }
                                currentBuild.description += "${params.SOURCE_BRANCH}"
                                currentBuild.description += "<br>-> ${params.TARGET_BRANCH}"
                            },
                            'status pending': {
                                setStatus(
                                        constant.pullRequestStatus.pending,
                                        "Unit test job initiated"
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

        stage('unit test') {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Unit Testing: Finger crossed..."
                    )
                    replaceFlavorType(constant.flavorType.mock)
                    runGradleTask('allUnitTestsDebug')
                    repository.storeReport('build/reports/unit-tests')
                    currentBuild.description += """<br><a href="http://localhost/jenkins/tuucho-report/${repository.relativePath()}/build/reports/unit-tests/index.html" target="_blank">Tests report</a>"""
                }
            }
        }

        stage('coverage') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                script {
                    setStatus(
                            constant.pullRequestStatus.pending,
                            "Coverage reporting"
                    )
                    runGradleTask('koverHtmlReport')
                    repository.storeReport('build/reports/kover/html')
                    currentBuild.description += """<br><a href="http://localhost/jenkins/tuucho-report/${repository.relativePath()}/build/reports/kover/html/index.html" target="_blank">Coverage report</a>"""
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
