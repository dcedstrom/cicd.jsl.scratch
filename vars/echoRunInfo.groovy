def call(Map config) {
    echo 'Showing input params'
    echo config

    echo 'Showing available env vars'
    sh "env"

}