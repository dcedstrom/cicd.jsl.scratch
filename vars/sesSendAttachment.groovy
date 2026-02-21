def call(Map config) {
    def toRaw = (config.emailTo ?: params.notify_emails ?: "").trim()
    def to = toRaw.split(',').collect { it.trim() }.findAll { it }
    if (!to) error("No recipients (config.emailTo or params.notify_emails).")

    def from    = config.fromEmail ?: "cicd@venerated.io"
    def subject = config.subject ?: "Jenkins Build - ${currentBuild.displayName} - ${currentBuild.currentResult}"
    def html    = config.emailBody ?: ""
    def pat     = (config.attachmentsPattern ?: "").trim()

    def files = pat ? findFiles(glob: pat).findAll { !it.directory }.collect { it.path } : []

    def boundary = "jenkins_${UUID.randomUUID().toString().replace('-','')}"
    def nl = "\r\n"

    def sb = new StringBuilder()
    sb << "From: ${from}${nl}"
    sb << "To: ${to.join(', ')}${nl}"
    sb << "Subject: ${subject}${nl}"
    sb << "MIME-Version: 1.0${nl}"
    sb << "Content-Type: multipart/mixed; boundary=\"${boundary}\"${nl}${nl}"

    // HTML part
    sb << "--${boundary}${nl}"
    sb << "Content-Type: text/html; charset=UTF-8${nl}"
    sb << "Content-Transfer-Encoding: 7bit${nl}${nl}"
    sb << "${html}${nl}${nl}"

    // Attachments
    files.each { path ->
        def name = path.tokenize('/').last()
        def bytes = readFile(file: path, encoding: "Base64") // Jenkins: returns base64 of file
        sb << "--${boundary}${nl}"
        sb << "Content-Type: application/octet-stream; name=\"${name}\"${nl}"
        sb << "Content-Disposition: attachment; filename=\"${name}\"${nl}"
        sb << "Content-Transfer-Encoding: base64${nl}${nl}"
        sb << "${bytes}${nl}${nl}"
    }

    sb << "--${boundary}--${nl}"

    // SES expects the *entire* raw message as a blob; with AWS CLI v2 this is easiest as base64 in JSON.
    def rawB64 = sb.toString().bytes.encodeBase64().toString()
    writeJSON file: "ses_raw.json", json: [ RawMessage: [ Data: rawB64 ] ]

    try {
        withAWS([credentials: config.awsCreds, roleAccount: config.sesAcctNum, region: config.awsRegion ?: "us-west-2", useNode: true]) {
            sh "aws ses send-raw-email --cli-binary-format raw-in-base64-out --raw-message file://ses_raw.json"
        }
    } finally {
        sh "rm -f ses_raw.json"
    }
}