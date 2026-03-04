def call(Map config = [:]) {
    Map cfg = [
        // Required Inputs
        mvnCmd        : config.mvnCmd,
        settingsFileId: config.settingsFileId ?: 'global-default-settings-xml', // settings.xml

        // IMPORTANT: default false so logs stream normally
        returnStdout  : (config.returnStdout ?: false) as boolean,

        // Feature flags
        skipJarDeploy : (config.skipJarDeploy ?: false) as boolean,

        // Explicit deploy (push artifact to maven repo) override switch
        caDeploy      : (config.caDeploy ?: false) as boolean,

        // Name of repo/server id in settings.xml file
        altRepoId     : config.altRepoId ?: 'ven-artifacts-maven-internal', // generali-artifacts-java-internal

        // CodeArtifact URL inputs (used when caDeploy=true)
        caRegion      : config.caRegion ?: 'us-east-1',
        caDomain      : config.caDomain ?: 'ven-artifacts', // generali-artifacts
        caOwner       : config.caOwner  ?: '495599744457', // 946166969745

        // Name of repo as defined in AWS (CodeArtifact repo name)
        caRepo        : config.caRepo   ?: 'maven-internal', // java-internal

        // Future proofing / Debugging
        caUrlOverride : (config.caUrlOverride ?: '').toString().trim(),

        extraArgs     : (config.extraArgs ?: '').toString().trim(),
    ]

    if (!cfg.mvnCmd) {
        error("mvnWrapper: mvnCmd is required")
    }

    // https://{domain}-{owner}.d.codeartifact.{region}.amazonaws.com/maven/{repo}/
    String derivedCaUrl = "https://${cfg.caDomain}-${cfg.caOwner}.d.codeartifact.${cfg.caRegion}.amazonaws.com/maven/${cfg.caRepo}/"
    String caUrl = cfg.caUrlOverride ? cfg.caUrlOverride : derivedCaUrl

    String cmd = cfg.mvnCmd.toString().trim()

    if (cfg.skipJarDeploy) {
        cmd = "${cmd} -Dmaven.deploy.skip=true"
    }

    if (cfg.caDeploy) {
        cmd = "${cmd} -DaltDeploymentRepository=${cfg.altRepoId}::default::${caUrl}"
    }

    if (cfg.extraArgs) {
        cmd = "${cmd} ${cfg.extraArgs}".trim()
    }

    configFileProvider([configFile(fileId: cfg.settingsFileId, variable: 'MAVEN_SETTINGS')]) {
        String finalCmd = "${cmd} -s \"\$MAVEN_SETTINGS\""
        return sh(script: finalCmd, returnStdout: cfg.returnStdout)
    }
}