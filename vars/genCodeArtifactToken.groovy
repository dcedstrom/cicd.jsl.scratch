def call(Map config) {
    withAWS(role: config.iam_role, region: config.aws_region) {
        def codeArtifactToken = sh(returnStdout: true, script: """
        aws codeartifact get-authorization-token --domain ${config.codeArtifactDomain} --domain-owner ${config.codeArtifactDomainOwner} --query authorizationToken --output text
    """).trim()
        return codeArtifactToken
    }
}