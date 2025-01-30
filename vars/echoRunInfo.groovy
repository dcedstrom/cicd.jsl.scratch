import groovy.json.JsonOutput

def call(Map config) {
    echo 'Showing input params'
    echo JsonOutput.prettyPrint(JsonOutput.toJson(config))

    echo 'Showing available env vars'
    sh "env | sort | jq -Rn '[inputs | split(\"=\") | {key:.[0], value:.[1]}] | from_entries'"

}