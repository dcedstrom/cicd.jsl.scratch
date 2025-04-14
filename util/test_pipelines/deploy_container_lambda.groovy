def String jenkinsNode = "dev-01"


node(jenkinsNode) {
    if (params.docker_tag == "latest_push") {
        echo "Getting last pushed tag from ECR..."
        withAWS(credentials: 'lambda-container-update',region: 'us-east-2') {
            latestTagJson = sh(
                script: """
                    aws ecr describe-images --repository-name ${params.ecr_repo_name} --region ${env.AWS_REGION} \\
                    --query 'sort_by(imageDetails, &imagePushedAt)[-1].imageTags[0]' --output text
                """,
                returnStdout: true
            ).trim()
        }

        if (latestTagJson == "None") {
            error("No tags found in ECR repo")
        } else {
            dockerTag = latestTagJson
            echo "Latest tag found: ${dockerTag}"
        }
    } else {
        dockerTag = params.docker_tag
        echo "Using provided image tag: ${dockerTag}"
    }

    stage('Update Lambda') {
        imageUri = "${params.ecr_repo_uri}:${dockerTag}"
        echo "Updating lambda ${params.lambda_name} to image ${imageUri}"
        withAWS(credentials: 'lambda-container-update', region:'us-east-2') {
            sh """
            aws lambda update-function-code \
            --function-name ${params.lambda_name} \
            --image-uri ${imageUri} \
            --region ${env.AWS_REGION}
        """
        }
    }
}