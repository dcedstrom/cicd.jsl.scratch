def call(Map config) {
    echo 'Sending notification...'
    def rawBody = libraryResource 'io/venerated/templates/build-results.html'
    def binding = [
            applicationName: env.JOB_BASE_NAME,
            sourceBranch   : env.GIT_BRANCH,
            buildNumber    : env.BUILD_NUMBER,
            userName       : currentBuild.getBuildCauses()[0].userId,
            buildUrl       : env.BUILD_URL,
            mavenCmd       : params.maven_command,
            testVar        : config.someVar
    ]

    if (config.condVar) {
        binding.condVar = config.condVar
    }

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