def call(Map config) {
    // Inputs
    // gitBranch
    cal = new Date().toCalendar()
    release = "${cal.get(Calendar.YEAR) % 100}.${cal.get(Calendar.MONTH).intdiv(3) + 1}"

    if (config.gitBranch.equals('master') || config.gitBranch.equals('main')) {
        updateVersionDB(release, config.releaseTable, config.serviceName)
    }

    if (config.gitBranch.startsWith('hotfix/')) {
        if (config.language == "java") {
            hotfixVersion = sh(script: "mvn help:evaluate -Dexpression=project.version -q -DforceStdout", returnStdout: true)
        } else if (config.language == "nodejs") {
            // todo: how to make this optional or maybe set default?
            hotfixVersion = sh(script: "jq -r '.version' ${packageJsonFolder}/package.json", returnStdout: true).trim()
        } else if (config.language == "python") {
            hotfixVersion = sh(script: "egrep -o '([[:digit:]]|\\.)+' version.py", returnStdout: true).trim()
        }

        (hotfixYear, hotfixQuarter, build, revision) = hotfixVersion.tokenize('.')
        release = "${hotfixYear}.${hotfixQuarter}"
        revision = revision.toInteger() + 1
    }

    releaseVersion = "${release}.${build}.${revision}"
    sshagent([gitCredentials]) {
        sh(script: "git checkout ${params.git_branch}", returnStdout: true)
        sh(script: "git pull", returnStdout: true)

        if (language == "python") {
            sh(script: "echo \"__version__ = '${releaseVersion}'\" > version.py", returnStdout: true)
            sh(script: "git add version.py", returnStdout: true)
            sh(script: "git commit -m 'JENKINS: setting version in version.py to ${releaseVersion}'", returnStdout: true)
        } else if (language == "nodejs") {
            sh(script: "jq --arg VERSION '${releaseVersion}' '.version = \$VERSION' ${packageJsonFolder}/package.json > package-new-version.json", returnStdout: true)
            sh(script: "mv package-new-version.json ${packageJsonFolder}/package.json", returnStdout: true)
            sh(script: "git add ${packageJsonFolder}/package.json", returnStdout: true)
            sh(script: "git commit -m 'JENKINS: setting version in package.json to ${releaseVersion}'", returnStdout: true)
        } else if (language == "java") {
            sh(script: "mvn versions:set -DnewVersion=${releaseVersion} -DgenerateBackupPoms=false", returnStdout: true)
            sh(script: "shopt -s globstar &amp;&amp; git add **/pom.xml", returnStdout: true)
            sh(script: "git commit -m 'JENKINS: setting version in pom to ${releaseVersion}'", returnStdout: true)
        }

        sh(script: "git push", returnStdout: true)

        sh(script: "git tag ${releaseVersion}", returnStdout: true)
        sh(script: "git push origin ${releaseVersion}", returnStdout: true)

    }
}