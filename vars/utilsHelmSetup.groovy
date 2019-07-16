def call(Map params = [:]) {
    def awsRegion = params.get('awsRegion')
    def eksClusterName = params.get('eksClusterName')
    def repoName = params.get('repoName')
    def repoUrl = params.get("repoUrl")

    sh """
        aws eks --region ${awsRegion} update-kubeconfig --name ${eksClusterName}
        helm repo add ${repoName} ${repoUrl}
    """
}