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
                    impacted_resources: [
                        name: config.service_name ?: env.JOB_BASE_NAME,
                        type: "service"
                    ],
                    change_metadata: [
                        user_name: triggeredBy
                        resource_link: env.BUILD_URL
                    ]
                ]
            ]
        ]
    ]
    
    def jsonBody = groovy.json.JsonOutput.toJson(eventData)

    // Send event to Datadog using credentials
    withCredentials([
        string(credentialsId: 'datadog-api-key', variable: 'DATADOG_API_KEY'),
        string(credentialsId: 'datadog-app-key', variable: 'DATADOG_APP_KEY')
    ]) {
        def response = httpRequest(
            url: 'https://api.datadoghq.com/api/v2/events',
            httpMode: 'POST',
            customHeaders: [
                [name: 'Content-Type', value: 'application/json'],
                [name: 'DD-API-KEY', value: env.DATADOG_API_KEY],
                [name: 'DD-APPLICATION-KEY', value: env.DATADOG_APP_KEY]
            ],
            requestBody: jsonBody
        )
        
        if (response.status != 202) {
            echo "Warning: Failed to send event to Datadog. Status: ${response.status}"
            echo "Response: ${response.content}"
        }
    }
}