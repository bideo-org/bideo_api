pipeline {
    agent any

    stages {
        stage('Deploy Backend') {
            steps {
                sh '''
                    set -eux
                    echo "🚀 Starting backend deployment pipeline..."

                    # 1. Determine Docker Compose command
                    DOCKER_COMPOSE="docker-compose"
                    if ! command -v docker-compose >/dev/null 2>&1; then
                        DOCKER_COMPOSE="docker compose"
                    fi

                    # 2. Ensure Maven is installed and accessible
                    if ! command -v mvn >/dev/null 2>&1; then
                        echo "📦 Maven not found. Installing..."
                        apt-get update -qq && apt-get install -y -qq maven || {
                            curl -fsSL https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz | tar xz -C /opt/
                            export PATH="/opt/apache-maven-3.9.9/bin:$PATH"
                        }
                    fi

                    # 3. Configure Git safe directories for Jenkins user
                    git config --global --add safe.directory /home/rajit/Bideo_proj || true
                    git config --global --add safe.directory /home/rajit/Bideo_proj/bideo_api || true
                    git config --global --add safe.directory '*' || true

                    # 4. Pull latest backend code and package jar
                    echo "🔄 Pulling latest backend code..."
                    cd /home/rajit/Bideo_proj/bideo_api
                    git pull --rebase

                    echo "🔨 Building backend package with Maven..."
                    mvn clean package -DskipTests

                    # 5. Rebuild and restart only backend container
                    cd /home/rajit/Bideo_proj
                    echo "📦 Identifying backend service in docker-compose..."

                    SERVICE_NAME="bideo_api"
                    if ! $DOCKER_COMPOSE ps --services 2>/dev/null | grep -qx "bideo_api"; then
                        if $DOCKER_COMPOSE ps --services 2>/dev/null | grep -qx "backend"; then
                            SERVICE_NAME="backend"
                        elif $DOCKER_COMPOSE ps --services 2>/dev/null | grep -qx "api"; then
                            SERVICE_NAME="api"
                        else
                            SERVICE_NAME=""
                        fi
                    fi

                    if [ -n "$SERVICE_NAME" ]; then
                        echo "🔄 Rebuilding and restarting single service: ${SERVICE_NAME}..."
                        $DOCKER_COMPOSE build "$SERVICE_NAME"
                        $DOCKER_COMPOSE up -d --no-deps "$SERVICE_NAME"
                    else
                        echo "⚠️ Backend service name not matched, falling back to full build..."
                        $DOCKER_COMPOSE build
                        $DOCKER_COMPOSE up -d
                    fi

                    # 6. Clean up dangling images to preserve disk space
                    echo "🧹 Pruning unused images..."
                    docker image prune -f

                    echo "✅ Backend deployment complete!"
                '''
            }
        }
    }

    post {
        success {
            echo 'Deployment successful'
        }

        failure {
            echo 'Deployment failed'
        }
    }
}
