def call(pipeParams) {

    assert pipeParams.get('BUILD_AGENT') != null
    assert pipeParams.get('RELEASE_BRANCH', 'master') != null
    assert pipeParams.get('REPO_TYPE', 'ivy') != null
    assert pipeParams.get('ARTIFACTORY_URL') != null
    assert pipeParams.get('ARTIFACTORY_REPO_DEV') != null
    assert pipeParams.get('ARTIFACTORY_REPO_RELEASE') != null
    assert pipeParams.get('CRED_ARTIFACTORY_RW_DEV') != null
    assert pipeParams.get('CRED_ARTIFACTORY_RW_RELEASE') != null
    assert pipeParams.get('CRED_BITBUCKET_SSH_KEY') != null

    // input example
    // [BUILD_AGENT: 'build-agent',
    //  RELEASE_BRANCH: 'master',
    //  REPO_TYPE: 'ivy',
    //  ARTIFACTORY_URL: 'https://lx64905.sbcore.net:8443/artifactory',
    //  ARTIFACTORY_REPO_DEV: 'migration-development-local',
    //  ARTIFACTORY_REPO_RELEASE: 'migration-development-local',
    //  CRED_ARTIFACTORY_RW_DEV: 'c2e0ed7b-c8ff-4781-8106-623d449524b7',
    //  CRED_ARTIFACTORY_RW_RELEASE: 'c2e0ed7b-c8ff-4781-8106-623d449524b7',
    //  CRED_BITBUCKET_SSH_KEY: '9dd8e07e-a963-4f6d-9050-c86282e4520f']

    pipeline {
        agent {
            label "${pipeParams.BUILD_AGENT}"
        }

        parameters {
            string(name: 'OVERRIDE_VERSION',
                    defaultValue: '',
                    description: 'Override release version (only for release branches). Should be in format X.Y.Z (e.g. 1.2.3)')
        }

        environment {
            PIPELINE_NAME = "${env.JOB_NAME}"
            BITBUCKET_SSH_KEY = credentials("${pipeParams.CRED_BITBUCKET_SSH_KEY}")
        }

        options {
            skipDefaultCheckout(true)
        }

        stages {
            stage("Prepare") {
                steps {
                    script {
                        step([$class: 'WsCleanup'])
                    }
                }
            }

            stage("Checkout") {
                steps {
                    script {
                        checkout([$class: 'GitSCM',
                                  branches: [[name: env.BRANCH_NAME]],
                                  doGenerateSubmoduleConfigurations: scm.doGenerateSubmoduleConfigurations,
                                  extensions: [[$class: 'LocalBranch'], [$class: 'CloneOption', noTags: false, shallow: false, depth: 0, reference: '']],
                                  userRemoteConfigs: scm.userRemoteConfigs
                        ])

                        // git commit is required for bitbucketHandler
                        env.GIT_COMMIT = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
                        bitbucketHandler.notifyBuildStart displayName: env.PIPELINE_NAME,
                                displayMessage: 'Build started'
                        sh './gradlew -v'
                    }
                }
            }

            stage("Assemble and test") {
                steps{
                    sh './gradlew assemble test --stacktrace'
                }
                post {
                    always {
                        junit allowEmptyResults: true,
                              testResults: 'build/test-results/**/*.xml'
                    }
                }
            }

            stage("Dev release") {
                when { not { branch "$pipeParams.RELEASE_BRANCH" } }
                environment {
                    ARTIFACTORY_PUBLISH_REPO = "${pipeParams.ARTIFACTORY_URL}/${pipeParams.ARTIFACTORY_REPO_DEV}"
                    ARTIFACTORY_RW_USER = credentials("${pipeParams.CRED_ARTIFACTORY_RW_DEV}")
                }

                steps {
                    sh "./gradlew" +
                            " -Dorg.ajoberstar.grgit.auth.ssh.private=${env.BITBUCKET_SSH_KEY}" +
                            " -Dorg.ajoberstar.grgit.auth.session.config.StrictHostKeyChecking=no" +
                            " -PpublishRepoType=${pipeParams.REPO_TYPE}" +
                            " -PpublishRepoURL=${env.ARTIFACTORY_PUBLISH_REPO}" +
                            " -PpublishPassword=${env.ARTIFACTORY_RW_USER_PSW}" +
                            " -PpublishUsername=${env.ARTIFACTORY_RW_USER_USR}" +
                            " --stacktrace" +
                            " devSnapshot"
                }
            }

            stage("Release release") {
                when { branch "$pipeParams.RELEASE_BRANCH" }
                environment {
                    ARTIFACTORY_PUBLISH_REPO = "${pipeParams.ARTIFACTORY_URL}/${pipeParams.ARTIFACTORY_REPO_RELEASE}"
                    ARTIFACTORY_RW_USER = credentials("${pipeParams.CRED_ARTIFACTORY_RW_RELEASE}")
                }

                steps {
                    script {
                        def versionParam = (params.OVERRIDE_VERSION == '') ? '' : " -Prelease.version=${params.OVERRIDE_VERSION}"
                        sh "./gradlew" +
                                " -Dorg.ajoberstar.grgit.auth.ssh.private=${env.BITBUCKET_SSH_KEY}" +
                                " -Dorg.ajoberstar.grgit.auth.session.config.StrictHostKeyChecking=no" +
                                " -PpublishRepoType=${pipeParams.REPO_TYPE}" +
                                " -PpublishRepoURL=${env.ARTIFACTORY_PUBLISH_REPO}" +
                                " -PpublishPassword=${env.ARTIFACTORY_RW_USER_PSW}" +
                                " -PpublishUsername=${env.ARTIFACTORY_RW_USER_USR}" +
                                " --stacktrace" +
                                "${versionParam}" +
                                " final"
                    }
                }
            }
        }
        post {
            success {
                script {
                    bitbucketHandler.notifyBuildSuccess displayName: "${env.PIPELINE_NAME}",
                            displayMessage: 'Build passed'
                }
            }
            unsuccessful {
                script {
                    bitbucketHandler.notifyBuildFail displayName: "${env.PIPELINE_NAME}",
                            displayMessage: 'Build failed'
                }
            }
        }
    }
}