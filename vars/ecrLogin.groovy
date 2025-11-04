def call(Map config) {
    withAWS(region: config.region, credentials: config.credentials) {
        dockerLogin = sh("aws ecr get-login-password --region ${config.region} | docker login --username AWS --password-stdin ${config.repo}", returnStdout: true)
    }

    return dockerLogin
}