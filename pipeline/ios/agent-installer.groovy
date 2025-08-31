@Library('library@chore/add-ios-build') _

pipeline {
    agent {
        node {
            label 'master'
        }
    }

    parameters {
        booleanParam(name: 'INSTALL_BUILDER', defaultValue: false, description: 'Install builder agent')
        booleanParam(name: 'INSTALL_QA', defaultValue: false, description: 'Install qa agent')
    }

    options {
        ansiColor('xterm')
    }

    stages {
        stage('stash bash installer') {
            steps {
                script {
                    sh "mkdir -p helper"
                    if (params.INSTALL_BUILDER) {
                        sh "cp ${env.JENKINS_HELPER_FILES}/mac-agent-builder-installer.bash helper/"
                        stash name: 'mac-agent-builder-installer', includes: 'helper/mac-agent-builder-installer.bash'
                    }
                    if (params.INSTALL_QA) {
                        sh "cp ${env.JENKINS_HELPER_FILES}/mac-agent-qa-installer.bash helper/"
                        stash name: 'mac-agent-qa-installer', includes: 'helper/mac-agent-qa-installer.bash'
                    }
                }
            }
        }

        stage('Agent Builder Install') {
            when {
                expression { params.INSTALL_BUILDER }
            }
            steps {
                script {
                    node('ios-builder') {
                        unstash 'mac-agent-builder-installer'
                        sh "bash helper/mac-agent-builder-installer.bash"
                    }
                }
            }
        }

        stage('Agent QA Install') {
            when {
                expression { params.INSTALL_QA }
            }
            steps {
                script {
                    node('ios-qa') {
                        unstash 'mac-agent-qa-installer'
                        sh "bash helper/mac-agent-qa-installer.bash"
                    }
                }
            }
        }
    }
}
