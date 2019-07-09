def call(Map params = [:]) {
    def chartPath = params.get('chartPath')
    def targetPath = params.get('targetPath', chartPath)
    def version = params.get('version')

    sh "helm package -d ${targetPath} ${chartPath} --version ${version} --save=false"
    return sh(script: "ls -1 ${targetPath}/*.tgz 2> /dev/null", returnStdout: true).trim()
}