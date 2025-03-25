def call(Map config) {
    /*
    Inputs:
      - language
      - gitCredentials
      - dynamodbReleaseTable (Optional)
      - packageJsonFolder (Optional)
    */

    def cal = new Date().toCalendar()
    def release = "${cal.get(Calendar.YEAR) % 100}.${cal.get(Calendar.MONTH).intdiv(3) + 1}"
    def build, revision

    if (config.language == 'java') {
        def snapshots = sh(script: "mvn enforcer:enforce -Drules=requireReleaseDeps -DsearchTransitive=false", returnStatus: true)
        if (snapshots == 1) println "WARNING: SNAPSHOT dependencies found!"
    }

    if (params.git_branch.equals('main') || params.git_branch.equals('feature/test-main'))  {
        def versionData = dynamoVersioning([
            release: release,
            dynamodbReleaseTable: config.dynamodbReleaseTable
        ])
        build = versionData.build
        revision = versionData.revision
    } else if (params.git_branch.startsWith('hotfix/')) {
        def hotfixVersion
        if (config.language == "python") {
            hotfixVersion = sh(script: "egrep -o '([[:digit:]]|\\.)+' version.py", returnStdout: true).trim()
        } else if (config.language == "nodejs") {
            hotfixVersion = sh(script: "jq -r '.version' ${config.packageJsonFolder}/package.json", returnStdout: true).trim()
        } else if (config.language == "java") {
            hotfixVersion = sh(script: "mvn help:evaluate -Dexpression=project.version -q -DforceStdout", returnStdout: true)
        }

        def (hotfixYear, hotfixQuarter, bld, rev) = hotfixVersion.tokenize('.')
        release = "${hotfixYear}.${hotfixQuarter}"
        build = bld.toInteger()
        revision = rev.toInteger() + 1
    }

    def releaseVersion = "${release}.${build}.${revision}"
    if (params.git_branch.equals('feature/test-main')) {
        releaseVersion += "-test"
    }

    println "Using Version ${releaseVersion}"

    // Update version file + Git
    sshagent([config.gitCredentials]) {
        sh 'git config user.name "Jenkins CICD"'
        sh 'git config user.email "cicd@venerated.io"'

        if (config.language == "python") {
            sh(script: "echo \"__version__ = '${releaseVersion}'\" > version.py", returnStdout: true)
            sh(script: "git add version.py", returnStdout: true)
            sh(script: "git commit -m 'JENKINS: setting version in version.py to ${releaseVersion}'", returnStdout: true)
        } else if (config.language == "nodejs") {
            sh(script: "jq --arg VERSION '${releaseVersion}' '.version = \$VERSION' ${config.packageJsonFolder}/package.json > package-new-version.json", returnStdout: true)
            sh(script: "mv package-new-version.json ${config.packageJsonFolder}/package.json", returnStdout: true)
            sh(script: "git add ${config.packageJsonFolder}/package.json", returnStdout: true)
            sh(script: "git commit -m 'JENKINS: setting version in package.json to ${releaseVersion}'", returnStdout: true)
        } else if (config.language == "java") {
            sh(script: "git checkout ${params.git_branch}", returnStdout: true)
            sh(script: "git pull", returnStdout: true)

            sh(script: "mvn versions:set -DnewVersion=${releaseVersion} -DgenerateBackupPoms=false", returnStdout: true)
            sh(script: "shopt -s globstar && git add **/pom.xml", returnStdout: true)
            sh(script: "git commit -m 'JENKINS: setting version in pom to ${releaseVersion}'", returnStdout: true)
        }

        sh(script: "git push", returnStdout: true)

        sh(script: "git tag ${releaseVersion}", returnStdout: true)
        sh(script: "git push origin ${releaseVersion}", returnStdout: true)
    }

    return releaseVersion
}