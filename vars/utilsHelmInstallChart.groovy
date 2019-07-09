def call(Map params = [:]) {
    def awsRegion = params.get('awsRegion')
    def eksClusterName = params.get('eksClusterName')
    def repoName = params.get('repoName')
    def chartName = params.get('chartName')
    def chartVersion = params.get('chartVersion')
    def releaseName = params.get('releaseName')
    def chartNamespace = params.get('chartNamespace')

    // TODO: Add aws login command.
    sh """
        aws eks --region ${awsRegion} update-kubeconfig --name ${eksClusterName}
        helm install --replace ${repoName}/${chartName} \
            --version ${chartVersion} \
            --namespace ${chartNamespace} \
            --name ${releaseName}
    """
}