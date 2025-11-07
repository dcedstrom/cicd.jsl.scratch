def call(Map config) {

    def binding = [
        // TODO: Do this or files? Think it's always one file unless java which
        imageName      : config.image_name, // TODO: this overlaps with serviceName and ecrRepo, need to pick one
        serviceName    : config.service_name ?: env.JOB_BASE_NAME,
        version        : config.version,
        srcImg         : config.src_img,
        abbrvCommit     : config.abbrv_commit,
        envSlug        : config.env_slug,
        buildType      : config.build_type, //?: 'maven', TODO: Default this or nah?
        ecrRepo        : config.ecr_repo ?: '', // Should be the artifact/container name e.g. aa-test, s3-av-scan
        extraTags      : config.extra_tags ? [],
        ecrAcct        : config.ecr_account ?: '946166969745',
        awsProfile     : config.aws_profile ?: 'jenkins-legacy-artifact-access',
        region         : config.aws_region ?: 'us-west-2',
    ]

// <tag>latest</tag>
// <tag>${project.version}</tag>
// <tag>${git.commit.id.abbrev}</tag> ${binding.abbrvCommit}
// <tag>${app.semanticVersion}</tag>

/*
ecrImageCopy([
    image_name: serviceName,
    env_slug: envPrefix,
    service_name: serviceName,
    version: versionTag,
    src_img: deployImg,
    abbrv_commit: commitId,
    build_type: buildType,
])

 */

    def tags
    def ecrUrl = "${binding.ecrAcct}.dkr.ecr.${binding.region}.amazonaws.com"
    def imgPushUrl = "${ecrUrl}/${binding.ecrRepo}"

    if (binding.buildType == 'maven') {
        tags = sh(script: "jq -r '.tags[]' target/jib-image.json",returnStdout: true).trim().split('\n') as List<String>
        sh(script: 'jq -r ".tags[]" target/jib-image.json', returnStdout: true)
    } else {
        tags = ["${envSlug}-latest", 'latest' , binding.abbrvCommit]
    }

    def imgTags = tags.plus(binding.extraTags)

    ecrLogin() // TODO: Can I just login with daemon or does this need to be used as part of the args?

    withAWS([role: binding.awsProfile, region: binding.region]) {
        for (tag in imgTags) {
            sh "docker tag ${binding.srcImg} ${imgPushUrl}:${tag}"
            sh "docker push ${imgPushUrl}:${tag}"
        }
    }

    return dockerLogin
}