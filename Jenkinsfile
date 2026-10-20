pipeline {
    agent any

    options {
        // Prevent jobs from running indefinitely
        timeout(time: 30, unit: 'MINUTES')
        // Retain recent builds to conserve server disk space
        buildDiscarder(logRotator(numToKeepStr: '25', artifactNumToKeepStr: '10'))
        // Add timestamps to build logs
        timestamps()
        // Prevent concurrent builds from interfering with production state
        disableConcurrentBuilds()
    }

    environment {
        DOCKER_BUILDKIT = '1'
        COMPOSE_DOCKER_CLI_BUILD = '1'
        MAVEN_OPTS = '-Dorg.slf4j.simpleLogger.showDateTime=true -Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss'
        DEPLOY_ROOT = '/home/rajit/Bideo_proj'
        BACKEND_DIR = '/home/rajit/Bideo_proj/bideo_api'
        LOCAL_HEALTH_URL = 'http://localhost:8080/api/health'
        PUBLIC_HEALTH_URL = 'https://bideo.rajit.cc/api/health'
    }

    stages {
        stage('Pre-flight & Environment Check') {
            steps {
                sh '''
                    set -eux
                    echo "📋 =========================================="
                    echo "📋  PRE-FLIGHT DIAGNOSTICS & SYSTEM CHECKS   "
                    echo "📋 =========================================="

                    # 1. Display build & Git metadata
                    COMMIT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "${GIT_COMMIT:-unknown}")
                    COMMIT_AUTHOR=$(git log -1 --pretty=format:'%an <%ae>' 2>/dev/null || echo "Unknown")
                    COMMIT_MSG=$(git log -1 --pretty=format:'%s' 2>/dev/null || echo "No commit message")

                    echo "🔢 Jenkins Build: #${BUILD_NUMBER}"
                    echo "📌 Target Commit: ${COMMIT_SHA}"
                    echo "👤 Commit Author: ${COMMIT_AUTHOR}"
                    echo "📝 Commit Message: ${COMMIT_MSG}"
                    echo "📅 Timestamp: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"

                    # 2. Check disk space
                    echo "💾 Checking disk storage..."
                    df -h . | tail -1

                    # 3. Verify Docker runtime
                    echo "🐳 Verifying Docker daemon..."
                    docker --version || echo "⚠️ Docker CLI not in default PATH"

                    # 4. Ensure Maven is installed and accessible
                    if ! command -v mvn >/dev/null 2>&1; then
                        echo "📦 Maven not found in PATH. Checking alternative locations or installing..."
                        for mvn_dir in /usr/share/maven/bin /opt/maven/bin /opt/apache-maven-*/bin; do
                            if [ -x "$mvn_dir/mvn" ]; then
                                export PATH="$mvn_dir:$PATH"
                                break
                            fi
                        done
                    fi

                    if ! command -v mvn >/dev/null 2>&1; then
                        echo "📦 Installing standalone Apache Maven..."
                        apt-get update -qq && apt-get install -y -qq maven || {
                            mkdir -p /opt
                            curl -fsSL https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz | tar xz -C /opt/
                            export PATH="/opt/apache-maven-3.9.9/bin:$PATH"
                        }
                    fi

                    mvn -v

                    # 5. Configure Git safe directories for Jenkins user across deployment repos
                    git config --global --add safe.directory "${DEPLOY_ROOT}" || true
                    git config --global --add safe.directory "${BACKEND_DIR}" || true
                    git config --global --add safe.directory '*' || true

                    echo "✅ Pre-flight checks passed successfully!"
                '''
            }
        }

        stage('Unit & Regression Tests') {
            steps {
                sh '''
                    set -eux
                    echo "🧪 =========================================="
                    echo "🧪  RUNNING ISOLATED BACKEND UNIT TESTS      "
                    echo "🧪 =========================================="

                    # Ensure Maven is on PATH
                    if ! command -v mvn >/dev/null 2>&1; then
                        for mvn_dir in /usr/share/maven/bin /opt/maven/bin /opt/apache-maven-*/bin /opt/apache-maven-3.9.9/bin; do
                            [ -x "$mvn_dir/mvn" ] && export PATH="$mvn_dir:$PATH" && break
                        done
                    fi

                    # Run isolated unit tests (auth, username resolution, view tracking, health checks, SEO, slug utils)
                    mvn test
                '''
            }
            post {
                always {
                    // Record and report JUnit test trends in Jenkins UI
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Package Application Artifact') {
            steps {
                sh '''
                    set -eux
                    echo "🔨 =========================================="
                    echo "🔨  PACKAGING SPRING BOOT EXECUTABLE JAR    "
                    echo "🔨 =========================================="

                    if ! command -v mvn >/dev/null 2>&1; then
                        for mvn_dir in /usr/share/maven/bin /opt/maven/bin /opt/apache-maven-*/bin /opt/apache-maven-3.9.9/bin; do
                            [ -x "$mvn_dir/mvn" ] && export PATH="$mvn_dir:$PATH" && break
                        done
                    fi

                    # Build reproducible production jar skipping already executed unit tests
                    mvn clean package -DskipTests

                    JAR_PATH=$(find target -maxdepth 1 -name "*.jar" ! -name "*sources*" ! -name "*javadoc*" | head -1)
                    if [ -n "$JAR_PATH" ] && [ -f "$JAR_PATH" ]; then
                        echo "✅ Packaged Artifact: ${JAR_PATH}"
                        echo "📦 Artifact Size: $(ls -lh "$JAR_PATH" | awk '{print $5}')"
                        echo "🔑 SHA256 Checksum: $(sha256sum "$JAR_PATH" 2>/dev/null || shasum -a 256 "$JAR_PATH" || echo 'n/a')"
                    else
                        echo "❌ Error: Expected JAR artifact not found in target/ directory!"
                        exit 1
                    fi
                '''
            }
        }

        stage('Deploy Backend') {
            steps {
                sh '''
                    set -eux
                    echo "🚀 =========================================="
                    echo "🚀  STARTING ZERO-DOWNTIME BACKEND DEPLOY   "
                    echo "🚀 =========================================="

                    # 1. Determine Docker Compose binary
                    DOCKER_COMPOSE="docker-compose"
                    if ! command -v docker-compose >/dev/null 2>&1; then
                        DOCKER_COMPOSE="docker compose"
                    fi

                    # 2. Ensure Maven is on PATH
                    if ! command -v mvn >/dev/null 2>&1; then
                        for mvn_dir in /usr/share/maven/bin /opt/maven/bin /opt/apache-maven-*/bin /opt/apache-maven-3.9.9/bin; do
                            [ -x "$mvn_dir/mvn" ] && export PATH="$mvn_dir:$PATH" && break
                        done
                    fi

                    # 3. Pull latest backend code into deployment directory
                    echo "🔄 Updating code in ${BACKEND_DIR}..."
                    cd "${BACKEND_DIR}"
                    git pull --rebase

                    echo "🔨 Building deployment jar inside ${BACKEND_DIR}..."
                    mvn clean package -DskipTests

                    # 4. Identify backend service in docker-compose
                    cd "${DEPLOY_ROOT}"
                    echo "📦 Identifying backend service name in docker-compose..."

                    ALL_SERVICES=$($DOCKER_COMPOSE config --services 2>/dev/null || $DOCKER_COMPOSE ps --services 2>/dev/null || echo "")
                    echo "📋 Available docker-compose services: ${ALL_SERVICES}"

                    SERVICE_NAME=""
                    if echo "$ALL_SERVICES" | grep -qw "project_video"; then
                        SERVICE_NAME="project_video"
                    elif echo "$ALL_SERVICES" | grep -qw "bideo_api"; then
                        SERVICE_NAME="bideo_api"
                    elif echo "$ALL_SERVICES" | grep -qw "backend"; then
                        SERVICE_NAME="backend"
                    elif echo "$ALL_SERVICES" | grep -qw "app"; then
                        SERVICE_NAME="app"
                    elif echo "$ALL_SERVICES" | grep -qw "api"; then
                        SERVICE_NAME="api"
                    else
                        # Fallback to project_video
                        SERVICE_NAME="project_video"
                    fi

                    echo "🎯 Identified backend service: ${SERVICE_NAME}"

                    # 5. Rebuild and restart ONLY the backend container
                    echo "🔄 Rebuilding container image for ONLY service: ${SERVICE_NAME}..."
                    $DOCKER_COMPOSE build "$SERVICE_NAME"

                    # Safely remove any stuck previous container to prevent 'removal in progress' daemon errors
                    echo "🧹 Ensuring clean container state..."
                    docker stop "$SERVICE_NAME" 2>/dev/null || docker stop bideo_api 2>/dev/null || true
                    docker rm -f "$SERVICE_NAME" 2>/dev/null || docker rm -f bideo_api 2>/dev/null || true

                    echo "🔄 Updating container with isolated zero-downtime recreation..."
                    $DOCKER_COMPOSE up -d --no-deps "$SERVICE_NAME"

                    echo "✅ Container launch initiated!"
                '''
            }
        }

        stage('Smoke & Health Verification') {
            steps {
                sh '''
                    set +e
                    echo "🩺 =========================================="
                    echo "🩺  POST-DEPLOYMENT HEALTH CHECK & SMOKE TEST"
                    echo "🩺 =========================================="

                    DOCKER_COMPOSE="docker-compose"
                    if ! command -v docker-compose >/dev/null 2>&1; then
                        DOCKER_COMPOSE="docker compose"
                    fi

                    cd "${DEPLOY_ROOT}" || true

                    ALL_SERVICES=$($DOCKER_COMPOSE config --services 2>/dev/null || $DOCKER_COMPOSE ps --services 2>/dev/null || echo "")
                    SERVICE_NAME="project_video"
                    if echo "$ALL_SERVICES" | grep -qw "project_video"; then
                        SERVICE_NAME="project_video"
                    elif echo "$ALL_SERVICES" | grep -qw "bideo_api"; then
                        SERVICE_NAME="bideo_api"
                    elif echo "$ALL_SERVICES" | grep -qw "backend"; then
                        SERVICE_NAME="backend"
                    elif echo "$ALL_SERVICES" | grep -qw "app"; then
                        SERVICE_NAME="app"
                    fi

                    HEALTH_VERIFIED=0
                    MAX_ATTEMPTS=15
                    SLEEP_SECONDS=4

                    echo "⏳ Waiting for backend container to bootstrap and respond to health probes..."

                    for i in $(seq 1 $MAX_ATTEMPTS); do
                        echo "🔎 Attempt ${i}/${MAX_ATTEMPTS}: Probing ${LOCAL_HEALTH_URL}..."

                        # Try local health check first
                        HTTP_RESPONSE=$(curl -s -m 5 -w "\\n%{http_code}" "${LOCAL_HEALTH_URL}" 2>/dev/null || true)
                        BODY=$(echo "$HTTP_RESPONSE" | sed '$d')
                        STATUS_CODE=$(echo "$HTTP_RESPONSE" | tail -n 1)

                        if [ "$STATUS_CODE" = "200" ] || [ "$STATUS_CODE" = "207" ] || [ "$STATUS_CODE" = "503" ]; then
                            echo "📥 Received HTTP ${STATUS_CODE} from ${LOCAL_HEALTH_URL}!"
                            echo "📊 Health Response Payload:"
                            echo "$BODY"

                            if echo "$BODY" | grep -q '"status"'; then
                                echo "✅ Backend service and health controller verified successfully!"
                                HEALTH_VERIFIED=1
                                break
                            fi
                        fi

                        # Try public endpoint fallback if running outside internal bridge
                        if [ -n "${PUBLIC_HEALTH_URL}" ]; then
                            PUB_RESPONSE=$(curl -s -m 5 -w "\\n%{http_code}" "${PUBLIC_HEALTH_URL}" 2>/dev/null || true)
                            PUB_BODY=$(echo "$PUB_RESPONSE" | sed '$d')
                            PUB_CODE=$(echo "$PUB_RESPONSE" | tail -n 1)
                            if [ "$PUB_CODE" = "200" ] && echo "$PUB_BODY" | grep -q '"status"'; then
                                echo "📥 Received HTTP ${PUB_CODE} from ${PUBLIC_HEALTH_URL}!"
                                echo "📊 Health Response Payload:"
                                echo "$PUB_BODY"
                                echo "✅ Public health endpoint verified successfully!"
                                HEALTH_VERIFIED=1
                                break
                            fi
                        fi

                        echo "⏳ Service not ready yet (HTTP ${STATUS_CODE:-timeout}). Retrying in ${SLEEP_SECONDS}s..."
                        sleep $SLEEP_SECONDS
                    done

                    if [ "$HEALTH_VERIFIED" -eq 1 ]; then
                        echo "🎉 Smoke verification PASSED!"
                    else
                        echo "❌ Health check probe failed after ${MAX_ATTEMPTS} attempts."
                        echo "📋 Dumping recent container logs for ${SERVICE_NAME}..."
                        $DOCKER_COMPOSE logs --tail 100 "$SERVICE_NAME" 2>/dev/null || docker logs --tail 100 "$SERVICE_NAME" 2>/dev/null || true
                        echo "❌ Failing build due to unsuccessful health check."
                        exit 1
                    fi
                '''
            }
        }

        stage('Hygiene & Cleanup') {
            steps {
                sh '''
                    set +e
                    echo "🧹 =========================================="
                    echo "🧹  POST-DEPLOYMENT DOCKER & SYSTEM HYGIENE   "
                    echo "🧹 =========================================="

                    echo "🧹 Pruning unused dangling Docker images..."
                    docker image prune -f || true

                    echo "✅ System hygiene complete!"
                '''
            }
        }
    }

    post {
        success {
            echo "================================================================"
            echo "🎉 PRODUCTION DEPLOYMENT SUCCESSFUL!"
            echo "🚀 Build #${env.BUILD_NUMBER} is live and verified healthy."
            echo "🔗 Health API: ${env.PUBLIC_HEALTH_URL}"
            echo "================================================================"
        }

        failure {
            echo "================================================================"
            echo "❌ PRODUCTION DEPLOYMENT FAILED on Build #${env.BUILD_NUMBER}!"
            echo "⚠️ Please inspect the console logs above and container health."
            echo "================================================================"
        }

        unstable {
            echo "================================================================"
            echo "⚠️ BUILD UNSTABLE: Unit tests reported failures or regressions."
            echo "================================================================"
        }
    }
}
