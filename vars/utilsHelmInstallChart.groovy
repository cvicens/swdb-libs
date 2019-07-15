def call(Map params = [:]) {
    def repoName = params.get('repoName')
    def chartName = params.get('chartName')
    def chartVersion = params.get('chartVersion')
    def releaseName = params.get('releaseName')
    def chartNamespace = params.get('chartNamespace')

    sh """
        helm install --replace ${repoName}/${chartName} \
            --version ${chartVersion} \
            --namespace ${chartNamespace} \
            --name ${releaseName}
    """
}