def call(pipeParams) {
    assert pipeParams.get('RELEASE_BRANCH', 'master') != null
    assert pipeParams.get('BUILD_AGENT') != null
    assert pipeParams.get('CRED_BITBUCKET_SSH_KEY') != null
    println("Pipeline input arguments: \n" + pipeParams)

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
            REGISTRY = 'docker-swedbank.lx64905.sbcore.net'
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

            stage("Test") {
                steps{
                    sh './gradlew test --stacktrace'
                }
                post {
                    always {
                        junit allowEmptyResults: true,
                                testResults: 'build/test-results/**/*.xml'
                    }
                }
            }

            stage("Dev release jar") {
                when { not { branch "$pipeParams.RELEASE_BRANCH" } }

                steps {
                    sh "./gradlew" +
                            " -Dorg.ajoberstar.grgit.auth.ssh.private=${env.BITBUCKET_SSH_KEY}" +
                            " -Dorg.ajoberstar.grgit.auth.session.config.StrictHostKeyChecking=no" +
                            " -PskipRepoPublishing=true" +
                            " --stacktrace" +
                            " devSnapshot"
                }
            }

            stage("Release release jar") {
                when { branch "$pipeParams.RELEASE_BRANCH" }

                steps {
                    script {
                        def versionParam = (params.OVERRIDE_VERSION == '') ? '' : " -Prelease.version=${params.OVERRIDE_VERSION}"
                        sh "./gradlew" +
                                " -Dorg.ajoberstar.grgit.auth.ssh.private=${env.BITBUCKET_SSH_KEY}" +
                                " -Dorg.ajoberstar.grgit.auth.session.config.StrictHostKeyChecking=no" +
                                " -PskipRepoPublishing=true" +
                                " --stacktrace" +
                                "${versionParam}" +
                                " final"
                    }
                }
            }

            stage("Publish to docker") {
                steps {
                    sh '''
                        export PROJECT_NAME=`(gradle properties -q | grep "name:" | awk '{print $2}')`
                        export VERSION=`ls build/libs/*.jar | sed -r 's/.*\${PROJECT_NAME}-(.*).jar/\\1/\'`
                        echo $PROJECT_NAME
                        echo $VERSION
                        
                        docker build \
                            -t $REGISTRY/\$PROJECT_NAME:\$VERSION .
                        docket image push $REGISTRY/\$PROJECT_NAME:\$VERSION
                    '''
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