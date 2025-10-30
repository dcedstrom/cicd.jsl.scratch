def call(Map config) {

    def binding = [
        // TODO: Do this or files? Think it's always one file unless java which
        artifactFile   : config.artifact_file, // deploy_package
        serviceName    : config.service_name ?: env.JOB_BASE_NAME,
        version        : config.version,
        serviceLang    : config.svc_lang,
        domain         : config.ca_domain ?: 'ven-artifacts',
        owner          : config.ca_owner ?: '495599744457',
        region         : config.aws_region ?: 'us-east-2',
        pom_file       : config.pom_file ?: 'pom.xml',
        awsProfile     : config.aws_profile ?: 'lambda-container-update',
        settingsRepo   : config.setting_repo ?: 'ven-artifacts-maven-internal'
    ]

    def mavenRepo = "maven-internal"
    // All non-java artifacts
    def genericRepo = "generic"

    // Try, if fails log message, send email but don't fail
    // mvn deploy:deploy-file to codedeploy
    // Do this or use env var?
    env.ARTIFACT_TOKEN = genCodeArtifactToken([
        codeArtifactDomain: binding.domain,
        codeArtifactDomainOwner: binding.owner,
        AWSCredentialName: binding.awsProfile,
        aws_region: binding.region
    ])

//    echo "Generated token: ${token}"
      echo "Generatd token: ${env.ARTIFACT_TOKEN}"

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
        String pomFile      = binding.pom_file
        String artifactFile = "target/${binding.artifactFile}"
        if (!artifactFile)  error "caMirror(java): artifactFile is required"
        if (!fileExists(pomFile))      error "caMirror(java): pom not found: ${pomFile}"
        if (!fileExists(artifactFile)) error "caMirror(java): artifact not found: ${artifactFile}"

        // Maven CA URL format:
        // https://{domain}-{owner}.d.codeartifact.{region}.amazonaws.com/maven/{repo}/
        String caUrl = "https://${binding.domain}-${binding.owner}.d.codeartifact.${binding.region}.amazonaws.com/maven/${mavenRepo}/"

        // need a <server id="codeartifact"> in your global settings.xml, e.g.:
        //   <server>
        //     <id>codeartifact</id>
        //     <username>aws</username>
        //     <password>${env.CODEARTIFACT_AUTH_TOKEN}</password>
        //   </server>
        // TODO: This shouldn't be needed?
        // String settings = config.maven_settings ?: (env.GLOBAL_MAVEN_SETTINGS ?: '~/.m2/settings.xml')
        configFileProvider([configFile(fileId: 'global-default-settings-xml', variable: 'MAVEN_SETTINGS')]) {
            sh """
            mvn -B -DskipTests \\
                deploy:deploy-file \\
                -Dfile="${artifactFile}" \\
                -DpomFile="${binding.pom_file}" \\
                -DrepositoryId=${binding.settingsRepo} \\
                -Durl="${caUrl}" \\
                -s $MAVEN_SETTINGS
            """
        }

        echo "caMirror(java): deployed ${artifactFile} (POM: ${pomFile}) to ${caUrl}"

    } else {
        // -------- Generic (ZIP) --------
        // Expect: config.local (zip path). Optional: config.dest (key in repo).
        String local = config.artifact_name
        if (!fileExists(local)) error "caMirror(generic): file not found: ${local}"

        // Generic repo endpoint is discovered via AWS CLI:
        withAWS([credentials: binding.awsProfile, region: 'us-east-2']) {
            String endpoint = sh(returnStdout: true, script: """
      aws codeartifact get-repository-endpoint \
        --domain '${binding.domain}' --domain-owner '${binding.owner}' \
        --repository '${genericRepo}' --format generic \
        --query repositoryEndpoint --output text --region '${binding.region}'
    """).trim().replaceAll('/+\$', '')
            if (!endpoint) error "caMirror(generic): failed to resolve repository endpoint"

        }

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
        -H 'Authorization: Bearer $ARTIFACT_TOKEN' \\
        -H 'Content-Type: ${contentType}' \\
        --upload-file '${local}' \\
        '${url}'
    """
        echo "caMirror(generic): uploaded ${local} to ${url}"
    }



    println "Pushed to CodeArtifact successfully.."
}