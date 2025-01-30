def call(Map config, script) {
    echo "Param var..."
    try {
        echo "${params.param_var}"
    } catch (e) {
        echo "Param var did not work"
        echo "Exception ${e}"
    }

    echo "`config` var..."
    try {
        echo "${config.config_var}"
    } catch (e) {
        echo "Config var did not work"
        echo "Exception ${e}"
    }

    echo "Env Var..."
    try {
        echo "Build Number: ${env.BUILD_NUMBER}"
    } catch (e) {
        echo "Env var did not work"
        echo "Exception ${e}"
    }

    echo "Global var..."
    try {
        echo "$script.{pipeline_global_var}"
    } catch (e) {
        echo "Pipeline global var did not work"
        echo "Exception ${e}"
    }

    echo "Stage var..."
    try {
        echo "${script.pipeline_stage_var}"
    } catch (e) {
        echo "Pipeline stage var did not work"
        echo "Exception ${e}"
    }
}