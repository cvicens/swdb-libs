def call() {
    return envBaseParams() + [
            BUILD_AGENT: 'oc-docker-jdk11',
            REPO_TYPE_DEV: 'ivy',
            // push all the feature branch artifact to dev repo
            ARTIFACTORY_URL_DEV: 'https://lx64905.sbcore.net:8443/artifactory',
            ARTIFACTORY_REPO_DEV: 'migration-development-local',
            CRED_ARTIFACTORY_RW_DEV: '05965f62-807a-4eb3-9905-a1dbf9e10cd3',

            // TODO: check why maven publishing does not work!
            REPO_TYPE_RELEASE: 'maven',
            ARTIFACTORY_URL_RELEASE: 'http://repo1.swedbank.net:8081/artifactory',
            ARTIFACTORY_REPO_RELEASE: 'core-services',
            CRED_ARTIFACTORY_RW_RELEASE: '437ea3bc-7f44-4c16-b30a-100635658515',
            CRED_BITBUCKET_SSH_KEY: '081bd26a-f63e-4e79-9ccb-f6efdad85f3e',

            // docker params
            AWS_CREDENTIALS: 'c2624c2e-f7f5-4d9d-a363-925163980d6b',
            AWS_REGION: 'eu-west-1',
            ECR_REGISTRY: '971220085003.dkr.ecr.eu-west-1.amazonaws.com',
            PROXY_USER_CREDS: 'c5d62d01-367d-4b9b-9698-e29d45782e3d',

            // helm params
            HELM_REPO_NAME: 'andromeda-charts',
            HELM_REPO_URL: 's3://andromeda-helm-charts/charts'
    ]
}
