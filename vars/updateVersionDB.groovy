def call(Map config) {
    // Takes
    // - release
    // - releaseTable
    // - serviceName
    String releaseItem = (sh(returnStdout: true,
            script: """
aws dynamodb get-item --table-name ${config.releaseTable} \
--key '{"artifact": {"S": "${config.serviceName}"}}'
""")).trim()
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

    println("Updating release versioning DB with: release ${config.release}")
    sh """
aws dynamodb put-item --table-name ${config.releaseTable} \
--item '{
    "artifact": {"S": "${config.serviceName}"}, 
    "release": {"N": "${config.release}"},
    "build": {"N": "${build}"}, 
    "revision": {"N": "${revision}"}
}'
"""


}