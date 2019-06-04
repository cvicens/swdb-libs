def call(pipeParams) {
    assert pipeParams.get('RELEASE_BRANCH', 'master') != null
    assert pipeParams.get('BUILD_AGENT') != null
    assert pipeParams.get('CRED_BITBUCKET_SSH_KEY') != null
    assert pipeParams.get('AWS_CREDENTIALS') != null
    assert pipeParams.get('AWS_REGION') != null
    assert pipeParams.get('ECR_REGISTRY') != null
    assert pipeParams.get('PROXY_USER_CREDS') != null
    println("Pipeline input arguments: \n" + pipeParams)

    pipeline {
        agent {
            label "${pipeParams.BUILD_AGENT}"
        }

        parameters {
            string(name: 'OVERRIDE_VERSION',
                    defaultValue: '',
                    description: 'Override release version (only for release branches). Should be in format X.Y.Z (e.g. 1.2.3)')
            string(name: 'AWS_REGION', defaultValue: "${pipeParams.get('AWS_REGION')}")
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
                        sh 'chmod +x gradlew'
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

            stage("Publish image RELEASE") {
                when { branch "$pipeParams.RELEASE_BRANCH" }
                environment {
                    AWS_ID = credentials("${pipeParams.AWS_CREDENTIALS}")
                    AWS_ACCESS_KEY_ID = "${env.AWS_ID_USR}"
                    AWS_SECRET_ACCESS_KEY = "${env.AWS_ID_PSW}"
                    AWS_DEFAULT_REGION = "${params.AWS_REGION}"
                    AWS_REGION = "${params.AWS_REGION}"
                    REGISTRY = "${pipeParams.ECR_REGISTRY}"
                    PROXY_CREDS=credentials("${pipeParams.PROXY_USER_CREDS}")
                    http_proxy="http://$PROXY_CREDS@proxyvip.foreningssparbanken.se:8080/"
                    https_proxy="http://$PROXY_CREDS@proxyvip.foreningssparbanken.se:8080/"
                    no_proxy='localhost,127.0.0.1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,.sbcore.net,.swedbank.net'
                }

                steps {
                    sh '''
                        export PROJECT_NAME=`(./gradlew properties -q | grep "name:" | awk '{print \$2}')`
                        export VERSION=`(ls build/libs/*.jar | sed -r "s/.*\$PROJECT_NAME-(.*).jar/\\1/" | sed 's/[^a-zA-Z0-9\\.\\_\\-]//g')`
                       
                        # get login
                        \$(aws ecr get-login --no-include-email --no-verify-ssl --region ${AWS_REGION}) 
                        
                        # build
                        docker build -t $REGISTRY/\$PROJECT_NAME:\$VERSION \
                                     -t $REGISTRY/\$PROJECT_NAME:latest .
                        
                        # push
                        docker image push $REGISTRY/\$PROJECT_NAME:\$VERSION
                        docker image push $REGISTRY/\$PROJECT_NAME:latest
                    '''
                }
            }
            stage("Publish image DEV") {
                when { not { branch "$pipeParams.RELEASE_BRANCH" } }
                environment {
                    IMAGE_PREFIX = "prdev"
                    AWS_ID = credentials("${pipeParams.AWS_CREDENTIALS}")
                    AWS_ACCESS_KEY_ID = "${env.AWS_ID_USR}"
                    AWS_SECRET_ACCESS_KEY = "${env.AWS_ID_PSW}"
                    AWS_DEFAULT_REGION = "${params.AWS_REGION}"
                    AWS_REGION = "${params.AWS_REGION}"
                    REGISTRY = "${pipeParams.ECR_REGISTRY}"
                    PROXY_CREDS=credentials("${pipeParams.PROXY_USER_CREDS}")
                    http_proxy="http://$PROXY_CREDS@proxyvip.foreningssparbanken.se:8080/"
                    https_proxy="http://$PROXY_CREDS@proxyvip.foreningssparbanken.se:8080/"
                    no_proxy='localhost,127.0.0.1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,.sbcore.net,.swedbank.net'
                }
                steps {
                    sh '''
                        export PROJECT_NAME=`(./gradlew properties -q | grep "name:" | awk '{print \$2}')`
                        export VERSION=${IMAGE_PREFIX}-`(ls build/libs/*.jar | sed -r "s/.*\$PROJECT_NAME-(.*).jar/\\1/" | sed 's/[^a-zA-Z0-9\\.\\_\\-]//g')`
                       
                        # get login
                        \$(aws ecr get-login --no-include-email --no-verify-ssl --region ${AWS_REGION}) 
                        
                        # build
                        docker build -t $REGISTRY/\$PROJECT_NAME:\$VERSION .
                        
                        # push
                        docker image push $REGISTRY/\$PROJECT_NAME:\$VERSION
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