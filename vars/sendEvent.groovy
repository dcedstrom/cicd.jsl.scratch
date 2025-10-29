def call(Map config) {
    def buildTrigger = ""
    def triggeredBy = ""
    
    // Determine build trigger and who triggered it
    switch (config.current_build.getBuildCauses()[0]["_class"]) {
        case ('hudson.model.Cause$UserIdCause'):
            buildTrigger = "USER_TRIGGERED"
            triggeredBy = config.current_build.getBuildCauses()[0]['userId']
            break
        case ('hudson.triggers.TimerTrigger$TimerTriggerCause'):
            buildTrigger = "SCHEDULED"
            triggeredBy = "Timer"
            break
        case ('org.jenkinsci.plugins.workflow.cps.replay.ReplayCause'):
            buildTrigger = "REPLAY"
            triggeredBy = "Replay"
            break
        default:
            buildTrigger = "OTHER"
            triggeredBy = "Unknown"
            break
    }

    if (buildTrigger == "USER_TRIGGERED") {
        authorType = "user"
    } else {
        authorType = "system"
    }

    // Prepare event data
    def eventTitle = "Jenkins Build ${config.current_build.currentResult}"
    def eventMessage = """
        Build #${env.BUILD_NUMBER} ${config.current_build.currentResult}
        Project: ${config.service_name ?: env.JOB_BASE_NAME}
        Branch: ${env.BRANCH_NAME}
        Commit: ${env.GIT_COMMIT ?: 'unknown'}
        Version: ${config.version ?: 'unknown'}
        Artifact Source: ${config.artifact_source ?: 'unknown'}
        Artifact URL: ${config.artifact_url ?: 'N/A'}
        Triggered by: ${triggeredBy}
        Build URL: ${env.BUILD_URL}
    """.stripIndent()

    def tags = [
        "project:${config.service_name ?: env.JOB_BASE_NAME}",
        "environment:${config.environment ?: 'unknown'}",
        "status:${config.current_build.currentResult}",
        "trigger:${buildTrigger}",
        "branch:${env.BRANCH_NAME}"
    ]

    // Construct the V2 API request body
    def eventData = [
        data: [
            type: "event",
            attributes: [
                title: eventTitle,
                message: eventMessage,
                category: "change",
                aggregation_key: "${config.service_name ?: env.JOB_BASE_NAME}-${env.BUILD_NUMBER}",
                timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
                tags: tags,
                attributes: [
                    author: [
                        name: triggeredBy,
                        type: authorType
                    ],
                    changed_resource: [
                        name: config.service_name ?: env.JOB_BASE_NAME,
                        type: "configuration"
                    ],
                    impacted_resources: [[
                        name: config.service_name ?: env.JOB_BASE_NAME,
                        type: "service"
                    ]],
                    change_metadata: [
                        user_name: triggeredBy,
                        resource_link: env.BUILD_URL
                    ]
                ]
            ]
        ]
    ]
    
    def jsonBody = groovy.json.JsonOutput.toJson(eventData)

    // Send event to Datadog using credentials
    withCredentials([usernamePassword(
        credentialsId: 'gga-datadog-events', 
        usernameVariable: 'DATADOG_API_KEY', 
        passwordVariable: 'DATADOG_APP_KEY')]) {
        
        // Create a temporary file to store the JSON payload
        writeFile file: 'event_payload.json', text: jsonBody
        
        // Execute curl command with verbose output and separate headers/body
        def curlCommand = """
            curl -v -X POST 'https://api.datadoghq.com/api/v2/events' \\
            -H 'Content-Type: application/json' \\
            -H 'DD-API-KEY: ${env.DATADOG_API_KEY}' \\
            -H 'DD-APPLICATION-KEY: ${env.DATADOG_APP_KEY}' \\
            -d @event_payload.json \\
            -w '\\n%{http_code}\\n%{response_code}\\n' \\
            -D response_headers.txt \\
            -o response_body.txt
        """.stripIndent()
        
        // Execute curl and capture output
        def curlOutput = sh(script: curlCommand, returnStdout: true)
        
        // Read response files
        def headers = readFile('response_headers.txt').trim()
        def body = readFile('response_body.txt').trim()
        
        // Clean up temporary files
        // sh "rm -f event_payload.json response_headers.txt response_body.txt"
        
        // Parse the last line of curl output for status code
        def statusCode = curlOutput.trim().split('\n')[-1]
        
        // Log detailed information for debugging
        echo "=== Curl Verbose Output ==="
        echo curlOutput
        echo "=== Response Headers ==="
        echo headers
        echo "=== Response Body ==="
        echo body
        
        if (statusCode != "202") {
            echo "Warning: Failed to send event to Datadog. Status: ${statusCode}"
            echo "Full response details have been logged above"
        }
    }
}