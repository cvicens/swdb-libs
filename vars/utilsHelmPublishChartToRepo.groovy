def call(Map params = [:]) {
    def repoName = params.get("repoName")
    def repoUrl = params.get("repoUrl")
    def chartPackagePath = params.get("chartPackagePath")

    sh """
        helm repo add ${repoName} ${repoUrl}
        helm s3 push ${chartPackagePath} ${repoName} --force
        helm repo update
    """
}