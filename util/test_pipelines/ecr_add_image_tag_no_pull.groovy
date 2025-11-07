properties(
    [
        parameters([
            string(name: 'srcTag', defaultValue: 'latest', description: 'Existing tag to reference'),
            string(name: 'newTag', defaultValue: 'test-latest', description: 'The new tag to associate'),
            booleanParam(name: "standard_jib", defaultValue: true)
        ])
    ]
)

@Library('basic-util')
import io.venerated.*


node {

    withAWS(credentials: 'lambda-container-update', region: 'us-east-2') {
        def repoImg = "cicd.qa.java"
        def srcTag  = params.srcTag   // e.g., "qa-latest"
        def dstTag  = params.newTag  // e.g., "prod-latest"
        def acct = '495599744457'
        def region = 'us-east-2'


        echo "Retagging ${repo}:${srcTag} -> ${repo}:${dstTag}"

        // TODO: Shouldn't need to login with docker, it's all AWS CLI
//        loginResult = ecrLogin([region: region, credentials: 'lambda-container-update'])

        def manifest = sh(
            returnStdout: true,
            script: """
      aws ecr batch-get-image \
        --registry-id ${acct} \
        --repository-name ${repoImg} \
        --image-ids imageTag=${srcTag} \
        --accepted-media-types application/vnd.docker.distribution.manifest.list.v2+json \
                               application/vnd.docker.distribution.manifest.v2+json \
        --query 'images[0].imageManifest' \
        --output text
    """
        ).trim()

        if (!manifest) {
            error "Source tag not found: ${repoImg}:${srcTag}"
        }

        sh """
    aws ecr put-image \
      --registry-id ${acct} \
      --repository-name ${repoImg} \
      --image-tag ${dstTag} \
      --image-manifest '${manifest}'
  """
    }
}

