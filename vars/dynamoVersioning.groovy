def call(Map config) {
    // Inputs: release, dynamodbReleaseTable
    String releaseItem = sh(returnStdout: true, script: """
        aws dynamodb get-item --table-name ${config.dynamodbReleaseTable} \
        --key '{"artifact": {"S": "${params.service_name}"}}'
    """).trim()

    def build, revision
    if (releaseItem) {
        releaseProps = readJSON text: releaseItem
        println("Version from DB: ${releaseProps}")
        build = releaseProps.Item.build.N.toInteger() + 1
        revision = releaseProps.Item.revision.N
    } else {
        println("Version not found in DB. Adding as new.")
        build = 1
        revision = 0
    }

    sh """
        aws dynamodb put-item --table-name ${config.dynamodbReleaseTable} \
        --item '{
            "artifact": {"S": "${params.service_name}"}, 
            "release": {"N": "${config.release}"},
            "build": {"N": "${build}"}, 
            "revision": {"N": "${revision}"}
        }'
    """

    return [build: build, revision: revision]
}