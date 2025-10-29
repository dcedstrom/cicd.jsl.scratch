def call(Map config) {

    def binding = [
        // TODO: Do this or files? Think it's always one file unless java which
        artifactName   : config.artifact_name, // deploy_package
        serviceName    : config.service_name ?: env.JOB_BASE_NAME,
        version        : config.version,
        serviceLang    : config.svc_lang,
        domain         : config.ca_domain ?: "ven-artifacts",
        owner          : config.ca_owner ?: "495599744457",
        region         : config.aws_region ?: "us-east-2",
        pom_file       : config.pom_file ?: 'pom.xml',
        awsProfile     : config.aws_profile ?: 'lambda-container-update'
    ]

    mavenRepo = "maven-internal"
    // All non-java artifacts
    genericRepo = "generic"

    // Try, if fails log message, send email but don't fail
    // mvn deploy:deploy-file to codedeploy
    // Do this or use env var?
    def token = genCodeArtifactToken([
        codeArtifactDomain: binding.codeArtifactDomain,
        codeArtifactDomainOwner: binding.owner,
        AWSCredentialName: binding.awsProfile,
        region: binding.region
    ])


    // TODO: Don't think this is needed since it's done below
//    withAWS(role: binding.awsProfile, region: binding.awsRegion, useNode: true) {
//        def endpoint = sh(returnStdout: true, script: """
//    aws codeartifact get-repository-endpoint \
//      --domain '${binding.domain}' --domain-owner '${binding.owner}' \
//      --repository '${binding.repo}' --format generic \
//      --query repositoryEndpoint --output text --region '${binding.region}'
//  """).trim().replaceAll('/+\$', '')
//    }

    println "Pushing to CodeArtifact mirror..."

    // TODO: Is there a sensitive option flag for this? They expire quickly but just to be "proper"
    if (binding.serviceLang == 'java') {
        // -------- Java (Maven) --------
        // Expect: config.pom_file (pom.xml path), config.artifact_file (jar/war)
        String pomFile      = binding.pomFile
        String artifactFile = binding.artifactFile
        if (!artifactFile)  error "caMirror(java): artifact_file is required"
        if (!fileExists(pomFile))      error "caMirror(java): pom not found: ${pomFile}"
        if (!fileExists(artifactFile)) error "caMirror(java): artifact not found: ${artifactFile}"

        // Maven CA URL format:
        // https://{domain}-{owner}.d.codeartifact.{region}.amazonaws.com/maven/{repo}/
        String caUrl = "https://${config.domain}-${config.owner}.d.codeartifact.${config.region}.amazonaws.com/maven/${mavenRepo}/"

        // need a <server id="codeartifact"> in your global settings.xml, e.g.:
        //   <server>
        //     <id>codeartifact</id>
        //     <username>aws</username>
        //     <password>${env.CODEARTIFACT_AUTH_TOKEN}</password>
        //   </server>
        // TODO: This shouldn't be needed?
        // String settings = config.maven_settings ?: (env.GLOBAL_MAVEN_SETTINGS ?: '~/.m2/settings.xml')

        withEnv(["CODEARTIFACT_AUTH_TOKEN=${token}"]) {
            sh """
        mvn -B -s '${settings}' -DskipTests \
          -DaltReleaseDeploymentRepository=codeartifact::default:${caUrl} \
          -DaltSnapshotDeploymentRepository=codeartifact::default:${caUrl} \
          deploy:deploy-file -Dfile='${artifactFile}' -DpomFile='${pomFile}'
      """
        }
        echo "caMirror(java): deployed ${artifactFile} (POM: ${pomFile}) to ${caUrl}"

    } else {
        // -------- Generic (ZIP) --------
        // Expect: config.local (zip path). Optional: config.dest (key in repo).
        String local = config.artifact_name
        if (!fileExists(local)) error "caMirror(generic): file not found: ${local}"

        // Generic repo endpoint is discovered via AWS CLI:
        String endpoint = sh(returnStdout: true, script: """
      aws codeartifact get-repository-endpoint \
        --domain '${binding.domain}' --domain-owner '${binding.owner}' \
        --repository '${genericRepo}' --format generic \
        --query repositoryEndpoint --output text --region '${binding.region}'
    """).trim().replaceAll('/+\$', '')
        if (!endpoint) error "caMirror(generic): failed to resolve repository endpoint"

        // keep to url safe chars so encoding logic can be omitted
        String dest = binding.dest ?: local.tokenize('/').last()
        if (!(dest ==~ /^[A-Za-z0-9._\\/-]+$/)) {
            error "caMirror(generic): dest contains characters that require encoding. " +
                "Use only letters/digits/._-/ (dest='${dest}')"
        }
        String contentType = config.content_type ?: 'application/zip'
        String url = "${endpoint}/${dest}"

        echo "caMirror(generic): PUT ${local} -> ${url}"
        // Use Bearer (preferred). Basic 'aws:<token>' also works, but we stick to Bearer.
        sh """
      set -euo pipefail
      curl -sS -X PUT \\
        -H 'Authorization: Bearer ${token}' \\
        -H 'Content-Type: ${contentType}' \\
        --upload-file '${local}' \\
        '${url}'
    """
        echo "caMirror(generic): uploaded ${local} to ${url}"
    }



    println "Pushed to CodeArtifact successfully.."
}