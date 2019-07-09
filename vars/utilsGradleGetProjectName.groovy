def call() {
    sh(script: "(./gradlew properties -q | grep 'name:' | awk '{print \$2}')", returnStdout: true).trim()
}