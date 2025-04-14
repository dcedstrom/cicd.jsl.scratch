@Library('basic-util')
import io.venerated.*

def someVar
def config = [:]

node("dev-01") {
    someVar = "global_value"
    config['someVar'] = someVar
//    config['condVar'] = "conditionalAvailable"
    def mvn_cmd = params.maven_command
    stage("Test Stage") {
        sh "echo Entered stage to set var"

    }
    try {
        sendResult(config)
    }
    catch(ex) {
        println "Exception: ${ex}"
    }
}

