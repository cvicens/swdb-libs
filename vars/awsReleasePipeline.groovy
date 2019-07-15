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
            booleanParam(name: 'PUSH_DOCKER_IMAGE', defaultValue: true, description: 'Indicates whether the docker image should be pushed to the registry.')
            // parameters from the input map
            string(name: 'AWS_REGION', defaultValue: "${pipeParams.get('AWS_REGION')}")
            string(name: 'RELEASE_BRANCH', defaultValue: "${pipeParams.get('RELEASE_BRANCH')}")
            string(name: 'BUILD_AGENT', defaultValue: "${pipeParams.get('BUILD_AGENT')}")
            string(name: 'CRED_BITBUCKET_SSH_KEY', defaultValue: "${pipeParams.get('CRED_BITBUCKET_SSH_KEY')}")
            string(name: 'AWS_CREDENTIALS', defaultValue: "${pipeParams.get('AWS_CREDENTIALS')}")
            string(name: 'ECR_REGISTRY', defaultValue: "${pipeParams.get('ECR_REGISTRY')}")
            string(name: 'PROXY_USER_CREDS', defaultValue: "${pipeParams.get('PROXY_USER_CREDS')}")
            string(name: 'HELM_REPO_NAME', defaultValue: "${pipeParams.get('HELM_REPO_NAME')}")
            string(name: 'HELM_REPO_URL', defaultValue: "${pipeParams.get('HELM_REPO_URL')}")
        }

        environment {
            PIPELINE_NAME = "${env.JOB_NAME}"
            BITBUCKET_SSH_KEY = credentials("${params.CRED_BITBUCKET_SSH_KEY}")
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
                        // gradle file should be executable
                        // use 'git update-index --chmod=+x gradlew' in your repo
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
                when { not { branch "${params.RELEASE_BRANCH}" } }

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
                when { branch "${params.RELEASE_BRANCH}" }

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

            stage("Publish K8s Master") {
                when { branch "${params.RELEASE_BRANCH}" }
                environment {
                    AWS_ID = credentials("${params.AWS_CREDENTIALS}")
                    AWS_ACCESS_KEY_ID = "${env.AWS_ID_USR}"
                    AWS_SECRET_ACCESS_KEY = "${env.AWS_ID_PSW}"
                    AWS_DEFAULT_REGION = "${params.AWS_REGION}"
                    AWS_REGION = "${params.AWS_REGION}"
                    DOCKER_REGISTRY = "${params.ECR_REGISTRY}"
                    PROXY_CREDS=credentials("${params.PROXY_USER_CREDS}")
                    http_proxy="http://$PROXY_CREDS@proxyvip.foreningssparbanken.se:8080/"
                    https_proxy="http://$PROXY_CREDS@proxyvip.foreningssparbanken.se:8080/"
                    no_proxy='localhost,127.0.0.1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,.sbcore.net,.swedbank.net'
                    HELM_REPO_URL = "${params.HELM_REPO_URL}"
                    HELM_REPO_NAME = "${params.HELM_REPO_NAME}"
                    GRADLE_PROJECT_NAME = utilsGradleGetProjectName()
                    VERSION=sh(script: "ls build/libs/*.jar | sed -r 's/.*${GRADLE_PROJECT_NAME}-(.*).jar/\\1/' | sed 's/[^a-zA-Z0-9\\.\\_\\-]//g'", returnStdout: true).trim()
                }
                stages {
                    stage("Publish docker image") {
                        steps {
                            script {
                                utilsAwsBuildPublishDockerImage name: "${DOCKER_REGISTRY}/${GRADLE_PROJECT_NAME}",
                                        tags: ["${VERSION}", 'latest'],
                                        awsRegion: "${AWS_REGION}"
                            }
                        }
                    }

                    stage("Install helm chart") {
                        steps {
                            script {
                                def chartBasePath = "./kubernetes"
                                def chartName = sh(script: "ls -1 ${chartBasePath} | head -n 1", returnStdout: true).trim()

                                def packagePath = utilsHelmCreatePackage chartPath: "${chartBasePath}/${chartName}",
                                        version: "${env.VERSION}"

                                utilsHelmSetup repoName: "${HELM_REPO_NAME}",
                                        repoUrl: "${HELM_REPO_URL}",
                                        awsRegion: "${AWS_REGION}",
                                        eksClusterName: "core-cluster"

                                utilsHelmPublishChartToRepo repoName: "${HELM_REPO_NAME}",
                                        chartPackagePath: "${packagePath}"

                                def chartNamespace = "prod-${chartName}-${env.GIT_COMMIT}".substring(0,62)
                                utilsHelmInstallChart repoName: "${HELM_REPO_NAME}",
                                        chartName: "${chartName}",
                                        chartVersion: "${env.VERSION}",
                                        chartNamespace: "${chartNamespace}",
                                        releaseName: "prod-${env.VERSION}"
                            }
                        }
                    }
                }
            }

            stage("Publish K8s Dev") {
                when { not { branch "${params.RELEASE_BRANCH}" } }
                environment {
                    AWS_ID = credentials("${params.AWS_CREDENTIALS}")
                    AWS_ACCESS_KEY_ID = "${env.AWS_ID_USR}"
                    AWS_SECRET_ACCESS_KEY = "${env.AWS_ID_PSW}"
                    AWS_DEFAULT_REGION = "${params.AWS_REGION}"
                    AWS_REGION = "${params.AWS_REGION}"
                    DOCKER_REGISTRY = "${params.ECR_REGISTRY}"
                    PROXY_CREDS=credentials("${params.PROXY_USER_CREDS}")
                    http_proxy="http://$PROXY_CREDS@proxyvip.foreningssparbanken.se:8080/"
                    https_proxy="http://$PROXY_CREDS@proxyvip.foreningssparbanken.se:8080/"
                    no_proxy='localhost,127.0.0.1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,.sbcore.net,.swedbank.net'
                    HELM_REPO_URL = "${params.HELM_REPO_URL}"
                    HELM_REPO_NAME = "${params.HELM_REPO_NAME}"
                    GRADLE_PROJECT_NAME = utilsGradleGetProjectName()
                    VERSION=sh(script: "ls build/libs/*.jar | sed -r 's/.*${GRADLE_PROJECT_NAME}-(.*).jar/\\1/' | sed 's/[^a-zA-Z0-9\\.\\_\\-]//g'", returnStdout: true).trim()
                }
                stages {
                    stage("Publish docker image") {
                        steps {
                            script {
                                utilsAwsBuildPublishDockerImage name: "${DOCKER_REGISTRY}/${GRADLE_PROJECT_NAME}",
                                        tags: ["${VERSION}"],
                                        awsRegion: "${AWS_REGION}"
                            }
                        }
                    }

                    stage("Install helm chart") {
                        steps {
                            script {
                                def chartBasePath = "./kubernetes"
                                def chartName = sh(script: "ls -1 ${chartBasePath} | head -n 1", returnStdout: true).trim()

                                def packagePath = utilsHelmCreatePackage chartPath: "${chartBasePath}/${chartName}",
                                        version: "${env.VERSION}"

                                utilsHelmSetup repoName: "${HELM_REPO_NAME}",
                                        repoUrl: "${HELM_REPO_URL}",
                                        awsRegion: "${AWS_REGION}",
                                        eksClusterName: "core-cluster"

                                utilsHelmPublishChartToRepo repoName: "${HELM_REPO_NAME}",
                                        chartPackagePath: "${packagePath}"

                                def chartNamespace = "pr-${chartName}-${env.GIT_COMMIT}".substring(0,62)
                                utilsHelmInstallChart repoName: "${HELM_REPO_NAME}",
                                        chartName: "${chartName}",
                                        chartVersion: "${env.VERSION}",
                                        chartNamespace: "${chartNamespace}",
                                        releaseName: "pr-${env.GIT_COMMIT.substring(0, 5)}"
                            }
                        }
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