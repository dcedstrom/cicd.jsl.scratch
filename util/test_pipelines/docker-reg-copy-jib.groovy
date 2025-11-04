properties(
    [
        parameters([
            string(defaultValue: 'mvn clean deploy', name: 'maven_cmd'),
            string(name: 'git_branch', defaultValue: 'main', description: 'Git branch to build'),
            string(name: 'serviceRepo', defaultValue: 'git@github.com:dcedstrom/cicd.qa.java.git', description: 'Git repository URL'),
            booleanParam(name: "standard_jib", defaultValue: true)
        ])
    ]
)

@Library('basic-util')
import io.venerated.*

node {

    def imageName = "ghcr.io/yourorg/your-repo:latest"
    def gitCreds = "gh-dcedstrom"
    def pkgCreds = "gh-jenkins-packages"
    def releaseVersion = "${currentBuild.number}"
    def language = 'java'
    def ecrRepoBase = '495599744457.dkr.ecr.us-east-2.amazonaws.com'


    stage("Set up credentials") {
        withAWS(credentials: 'lambda-container-update', region: 'us-east-2') {
            // TODO: Why is this being done twice??
            // This should work for both internal and public repos?
            env.ARTIFACT_TOKEN = sh(script: """
                aws codeartifact get-authorization-token --domain ven-artifacts --domain-owner 495599744457 --region us-east-2 --query authorizationToken --output text
            """, returnStdout: true).trim()
        }

    }


    stage("Clone") {
        step([$class: 'WsCleanup'])
        checkout([$class           : 'GitSCM', branches: [[name: params.git_branch]], extensions: [],
                  userRemoteConfigs: [[credentialsId: gitCreds, url: params.serviceRepo]]])
    }

    stage('Build and Push Image') {

        //TODO: Change for non-java test
        def artName = sh(script: "mvn -q -DforceStdout help:evaluate -Dexpression=project.artifactId", returnStdout: true)
        def artVer = sh(script: "mvn -q -DforceStdout help:evaluate -Dexpression=project.version", returnStdout: true)
        def artFile = "${artName}-${artVer}.jar"
        echo "File to mirror: ${artFile}"

        withCredentials([usernamePassword(
            credentialsId: pkgCreds,
            usernameVariable: 'DOCKER_AUTH_USER',
            passwordVariable: 'DOCKER_AUTH_PASS'
        )
        ]) {
            env.DOCKER_REG_URL = "ghcr.io/dcedstrom"
            withAWS(credentials: 'lambda-container-update', region: 'us-east-2') {
                // This should work for both internal and public repos?
                env.ARTIFACT_TOKEN = sh(script: """
                aws codeartifact get-authorization-token --domain ven-artifacts --domain-owner 495599744457 --region us-east-2 --query authorizationToken --output text
            """, returnStdout: true).trim()
            }
            configFileProvider([configFile(fileId: 'global-default-settings-xml', variable: 'MAVEN_SETTINGS')]) {
                sh(script: "${params.maven_cmd} -s $MAVEN_SETTINGS", returnStdout: true).trim()

                ecrLogin([credentials: 'lambda-container-update', repo: ecrRepoBase, region: 'us-east-2'])
                // Testing how long it takes to just do a second build with override
                sh(script: "${params.maven_cmd} -s $MAVEN_SETTINGS -Djib.to.image=${ecrRepoBase}/${artName}", returnStdout: true).trim()


            }
            try {
                codeArtifactMirrorPush(
                    artifact_file: artFile,
                    version: releaseVersion,
                    svc_lang: language
                )
            } catch(err) {
                echo "[caMirror](error) Failed to push package: ${err}"
            }

            sh "docker image ls"

            // Standard docker login for github GHCR
            // Login to ECR
            // Copy image from one to the other


//            sh 'docker login ghcr.io -u $DOCKER_AUTH_USER  -p $DOCKER_AUTH_PASS'







        }
    }
}