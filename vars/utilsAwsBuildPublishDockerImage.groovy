def call(Map params = [:]) {
    def imageName = params.get('name')
    def imageTags = params.get('tags')
    def awsRegion = params.get('awsRegion')
    def buildPath = params.get('buildPath', '.')

    def tagsArgsString = imageTags.collect { name -> " -t ${imageName}:${name}" }.join('')

    sh """
        \$(aws ecr get-login --no-include-email --no-verify-ssl --region ${awsRegion} 2> /dev/null) 
        docker build ${tagsArgsString} ${buildPath}
    """

    imageTags.each { tag ->
        sh "docker image push ${imageName}:${tag}"
    }
}