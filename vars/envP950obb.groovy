def call() {
    return envBaseParams() + [
            BUILD_AGENT: 'build-agent',
            REPO_TYPE_DEV: 'ivy',
            REPO_TYPE_RELEASE: 'ivy',
            ARTIFACTORY_URL_DEV: 'https://lx64905.sbcore.net:8443/artifactory',
            ARTIFACTORY_URL_RELEASE: 'https://lx64905.sbcore.net:8443/artifactory',
            ARTIFACTORY_REPO_DEV: 'migration-development-local',
            ARTIFACTORY_REPO_RELEASE: 'migration-development-local',
            CRED_ARTIFACTORY_RW_DEV: 'c2e0ed7b-c8ff-4781-8106-623d449524b7',
            CRED_ARTIFACTORY_RW_RELEASE: 'c2e0ed7b-c8ff-4781-8106-623d449524b7',
            CRED_BITBUCKET_SSH_KEY: '9dd8e07e-a963-4f6d-9050-c86282e4520f',
    ]
}
