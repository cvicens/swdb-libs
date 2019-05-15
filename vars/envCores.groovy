def call() {
    return envBaseParams() + [
            BUILD_AGENT: 'oc-docker-jdk11',
            REPO_TYPE_DEV: 'ivy',
            // push all the feature branch artifact to dev repo
            ARTIFACTORY_URL_DEV: 'https://lx64905.sbcore.net:8443/artifactory',
            ARTIFACTORY_REPO_DEV: 'migration-development-local',
            CRED_ARTIFACTORY_RW_DEV: '05965f62-807a-4eb3-9905-a1dbf9e10cd3',

            REPO_TYPE_RELEASE: 'maven',
            ARTIFACTORY_URL_RELEASE: 'http://repo1.swedbank.net:8081/artifactory',
            ARTIFACTORY_REPO_RELEASE: 'core-services',
            CRED_ARTIFACTORY_RW_RELEASE: 'cb8e052b-439c-4a5b-afc2-b84a88063c95',
            CRED_BITBUCKET_SSH_KEY: '081bd26a-f63e-4e79-9ccb-f6efdad85f3e',
    ]
}
