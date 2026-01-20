@Library('library@master') _

pipeline {
    agent none

    parameters {
        booleanParam(name: 'INSTALL_MAC_OS', defaultValue: false, description: 'Install mac os agent')
        booleanParam(name: 'INSTALL_BUILDER', defaultValue: false, description: 'Install builder agent')
        booleanParam(name: 'INSTALL_QA', defaultValue: false, description: 'Install qa agent')
    }

    options {
        ansiColor('xterm')
    }

    stages {
        stage('stash bash installer') {
            options {
                timeout(time: 1, unit: 'MINUTES')
            }
            agent {
                node {
                    label 'master'
                }
            }
            steps {
                script {
                    sh "mkdir -p helper"
                    if (params.INSTALL_MAC_OS) {
                        sh "cp ${env.JENKINS_HELPER_FILES}/mac-agent-builder-installer.bash helper/"
                        stash name: 'agent-mac-os-installer', includes: 'helper/mac-agent-builder-installer.bash'
                    }
                    if (params.INSTALL_BUILDER) {
                        sh "cp ${env.JENKINS_HELPER_FILES}/mac-agent-builder-installer.bash helper/"
                        stash name: 'agent-builder-installer', includes: 'helper/mac-agent-builder-installer.bash'
                    }
                    if (params.INSTALL_QA) {
                        sh "cp ${env.JENKINS_HELPER_FILES}/mac-agent-qa-installer.bash helper/"
                        stash name: 'agent-qa-installer', includes: 'helper/mac-agent-qa-installer.bash'
                    }
                }
            }
        }

        stage('Agent MacOs Install') {
            options {
                timeout(time: 25, unit: 'MINUTES')
            }
            when {
                expression { params.JENKINS_HELPER_FILES }
            }
            steps {
                script {
                    node('mac-os') {
                        unstash 'agent-mac-os-installer'
                        sh "bash helper/mac-agent-builder-installer.bash"
                    }
                }
            }
        }

        stage('Agent Builder Install') {
            options {
                timeout(time: 25, unit: 'MINUTES')
            }
            when {
                expression { params.INSTALL_BUILDER }
            }
            steps {
                script {
                    node('ios-builder') {
                        unstash 'agent-builder-installer'
                        sh "bash helper/mac-agent-builder-installer.bash"
                    }
                }
            }
        }

        stage('Agent QA Install') {
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            when {
                expression { params.INSTALL_QA }
            }
            steps {
                script {
                    node('ios-qa') {
                        unstash 'agent-qa-installer'
                        sh "bash helper/mac-agent-qa-installer.bash"
                    }
                }
            }
        }
    }
}