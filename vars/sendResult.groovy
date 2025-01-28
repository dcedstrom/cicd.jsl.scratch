def call(Map config) {
    echo 'Sending notification...'
    def rawBody = libraryResource 'io/venerated/templates/build-results.html'
    def binding = [
            applicationName: env.JOB_BASE_NAME ?: "Not Found",
            sourceBranch   : env.GIT_BRANCH ?: "Not Found",
            buildNumber    : env.BUILD_NUMBER ?: "Not Found",
            userName       : currentBuild.getBuildCauses()[0].userId ?: "Not Found",
            buildUrl       : env.BUILD_URL ?: "Not Found",
            mavenCmd       : params.maven_command ?: "Not Found",
            testVar        : config.someVar ?: "Not Found",
            condVar        : config.condVar ?: null
    ]

//    if (config.condVar) {
//        binding.condVar = config.condVar
//    }

    def render = renderTemplate(rawBody, binding)
    // TODO: Stdout for testing, swap to email plugin
    echo render
    def subjectLine = "${env.JOB_BASE_NAME} - ${env.BUILD_NUMBER} - ${currentBuild.currentResult}"
    echo subjectLine

    emailExt body: render,
        subject: subjectLine,
//        to: params.emailRecipients
        to: 'dedstrom@venerated.io'
}