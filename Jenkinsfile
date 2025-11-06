pipeline {
    agent any
    environment { 
        registry = "54788214/student-management"
        registryCredential = 'dockerhub'
        NVD_API_KEY = credentials('nvd-api-key')
        DOCKER_HOST = "tcp://localhost:2375"
    }

    stages {
        stage('📥 Checkout GitHub') {
            steps {
                git branch: 'main', 
                credentialsId: 'tokengithub', 
                url: 'https://github.com/oumaima-brahmi/student_management-.git'
            }
        }
        
        stage('🔨 Build Application') {
            steps {
                echo "Building Student Management with Java 21..."
                sh "mvn clean compile -DskipTests"
            }
        }

        stage('🧪 Run Tests') {
            steps {
                echo "Running tests..."
                sh "mvn test"
                post {
                    always {
                        jacoco(
                            execPattern: 'target/jacoco.exec',
                            classPattern: 'target/classes',
                            sourcePattern: 'src/main/java'
                        )
                    }
                }
            }
        }

        stage('📦 Package Application') {
            steps {
                echo "Packaging application..."
                sh "mvn package -DskipTests"
            }
        }

        stage('📊 Code Coverage') {
            steps {
                echo "Generating code coverage report..."
                sh "mvn jacoco:report"
                publishHTML([
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target/site/jacoco',
                    reportFiles: 'index.html',
                    reportName: 'Code Coverage Report'
                ])
            }
        }

        stage('🔒 Security Scan - SCA') {
            steps { 
                echo "Scanning dependencies for vulnerabilities..."
                script {
                    try {
                        sh """
                            mvn org.owasp:dependency-check-maven:check \
                            -DnvdApiKey=${NVD_API_KEY} \
                            -DautoUpdate=true \
                            -DfailBuildOnAnyVulnerability=false \
                            -Dformat=HTML \
                            -Dformat=JSON
                        """
                        echo "✅ Security scan successful with NVD API!"
                    } catch (Exception e) {
                        echo "⚠️  Online scan failed, using offline mode..."
                        sh """
                            mvn org.owasp:dependency-check-maven:check \
                            -DautoUpdate=false \
                            -DfailBuildOnAnyVulnerability=false \
                            -DfailOnError=false
                        """
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
            post {
                always {
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target',
                        reportFiles: 'dependency-check-report.html',
                        reportName: 'Security Scan Report'
                    ])
                    
                    // Archive le rapport JSON pour SonarQube
                    archiveArtifacts artifacts: 'target/dependency-check-report.json', allowEmpty: true
                }
            }
        }

        stage('⚡ Security Scan - SAST') {
            steps { 
                echo "Static Application Security Testing with SonarQube..."
                withSonarQubeEnv('mysonarqube') {
                    sh '''
                        mvn sonar:sonar \
                        -Dsonar.projectName=student-management \
                        -Dsonar.projectKey=student-management \
                        -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                        -Dsonar.dependencyCheck.jsonReportPath=target/dependency-check-report.json \
                        -Dsonar.java.coveragePlugin=jacoco
                    '''
                }
            }
        }

        stage('✅ Quality Gate') {
            steps { 
                echo "Waiting for quality gate result..."
                script {
                    timeout(time: 5, unit: 'MINUTES') {
                        def qg = waitForQualityGate()
                        if (qg.status != 'OK') {
                            error "❌ Pipeline stopped: Quality gate failed - ${qg.status}"
                        }
                        echo "✅ Quality gate passed: ${qg.status}"
                    }
                }
            }
        }
        
        stage('🐳 Build Docker Image') {
            steps { 
                echo "Building Docker image..."
                script {
                    // Vérifier que le Dockerfile existe
                    sh 'ls -la Dockerfile || echo "Dockerfile not found"'
                    
                    docker.withRegistry( '', registryCredential ) { 
                        def customImage = docker.build("${registry}:latest", "--build-arg JAR_FILE=target/student-management-0.0.1-SNAPSHOT.jar .")
                        customImage.push()
                        customImage.push('${BUILD_NUMBER}')
                    }
                }
            }
        }
        
        stage('🔍 Scan Docker Image') {
            steps { 
                echo "Scanning Docker image with Trivy..."
                sh """
                    # Scanner avec timeout et gestion d'erreur
                    timeout 300 trivy image \
                        --scanners vuln \
                        --severity CRITICAL,HIGH \
                        --exit-code 0 \
                        --format table \
                        ${registry}:latest > trivy-results.txt || echo "Trivy scan completed with findings"
                    
                    # Compter les vulnérabilités critiques
                    CRITICAL_COUNT=\$(grep -c "CRITICAL" trivy-results.txt || true)
                    echo "Found \$CRITICAL_COUNT critical vulnerabilities"
                """
                archiveArtifacts artifacts: 'trivy-results.txt'
                publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: '.',
                    reportFiles: 'trivy-results.txt',
                    reportName: 'Trivy Security Scan'
                ])
            }
        }
        
        stage('🚀 Smoke Test') {
            steps { 
                echo "Running smoke test..."
                script {
                    try {
                        // Démarrer le conteneur
                        sh """
                            docker run -d \
                                --name smokerun-${BUILD_NUMBER} \
                                -p 8089:8089 \
                                --health-cmd="curl -f http://localhost:8089/actuator/health || exit 1" \
                                --health-interval=10s \
                                ${registry}:latest
                        """
                        
                        // Attendre que l'application soit prête
                        sh """
                            for i in 1 2 3 4 5 6; do
                                if curl -f http://localhost:8089/student > /dev/null 2>&1; then
                                    echo "✅ Application is ready!"
                                    exit 0
                                fi
                                echo "⏳ Waiting for application... (\$i/6)"
                                sleep 10
                            done
                            echo "❌ Application failed to start"
                            exit 1
                        """
                        
                        // Test supplémentaire
                        sh "curl -f http://localhost:8089/student"
                        
                    } finally {
                        // Nettoyage garantie
                        sh "docker rm --force smokerun-${BUILD_NUMBER} || true"
                    }
                }
            }
        }
    }
    
    post {
        always {
            echo '🧹 Cleaning up...'
            sh '''
                docker rm --force smokerun-${BUILD_NUMBER} 2>/dev/null || true
                docker system prune -f || true
            '''
        }
        success {
            echo '🎉 FÉLICITATIONS ! Pipeline DevSecOps RÉUSSI ! 🎉'
            emailext (
                subject: "SUCCÈS Pipeline DevSecOps - Build #${BUILD_NUMBER}",
                body: """
                Le pipeline DevSecOps a réussi avec succès !
                
                Détails:
                - Application: Student Management
                - Build: #${BUILD_NUMBER}
                - Image Docker: ${registry}:latest
                - Rapport Sécurité: ${BUILD_URL}dependency-check-report/
                - Rapport Couverture: ${BUILD_URL}jacoco/
                
                Félicitations ! 🎉
                """,
                to: "votre-email@example.com"
            )
        }
        failure {
            echo '❌ Pipeline échoué. Vérifiez les logs pour les détails.'
            emailext (
                subject: "ÉCHEC Pipeline DevSecOps - Build #${BUILD_NUMBER}",
                body: """
                Le pipeline DevSecOps a échoué.
                
                Détails:
                - Build: #${BUILD_NUMBER}
                - URL du build: ${BUILD_URL}
                
                Veuillez vérifier les logs pour identifier le problème.
                """,
                to: "votre-email@example.com"
            )
        }
        unstable {
            echo '⚠️  Pipeline instable - vérifiez les scans de sécurité'
        }
    }
}
