def call(Map params = [:]) {
    def repoName = params.get("repoName")
    def chartPackagePath = params.get("chartPackagePath")

    sh """
        helm s3 push ${chartPackagePath} ${repoName} --force
        helm repo update
    """
}