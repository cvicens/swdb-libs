def call() {
    return envBaseParams() + [
            BUILD_AGENT: 'oc-docker-jdk11',
            REPO_TYPE_DEV: 'ivy',
            ARTIFACTORY_URL_DEV: 'http://lx64905.sbcore.net:8081/artifactory',
            ARTIFACTORY_REPO_DEV: 'migration-development-local',
            CRED_ARTIFACTORY_RW_DEV: '05965f62-807a-4eb3-9905-a1dbf9e10cd3',
            REPO_TYPE_RELEASE: 'ivy',
            ARTIFACTORY_URL_RELEASE: 'http://lx64905.sbcore.net:8081/artifactory',
            ARTIFACTORY_REPO_RELEASE: 'migration-development-local',
            CRED_ARTIFACTORY_RW_RELEASE: '05965f62-807a-4eb3-9905-a1dbf9e10cd3',
            CRED_BITBUCKET_SSH_KEY: '081bd26a-f63e-4e79-9ccb-f6efdad85f3e',
    ]
}
