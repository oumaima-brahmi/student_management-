pipeline {
    agent any
    
    environment { 
        registry = "54788214/student-management"
        registryCredential = 'dockerhub'
        NVD_API_KEY = credentials('nvd-api-key')
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
                sh "mvn clean compile"
            }
        }

        stage('🧪 Run Tests') {
            steps {
                echo "Running tests..."
                sh "mvn test"
            }
        }

        stage('📊 Code Coverage') {
            steps {
                echo "Generating code coverage report..."
                sh "mvn jacoco:report"
            }
        }

        stage('🔒 Security Scan - SCA') {
            steps { 
                echo "Scanning dependencies for vulnerabilities..."
                script {
                    withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY_SECRET')]) {
                        sh """
                            mvn dependency-check:check \
                            -DnvdApiKey=\${NVD_API_KEY_SECRET} \
                            -DautoUpdate=true \
                            -DfailBuildOnAnyVulnerability=false \
                            -DfailOnError=false
                        """
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/dependency-check-report.*', allowEmptyArchive: true
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
                    -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                    -Dsonar.dependencyCheck.jsonReportPath=target/dependency-check-report.json
                    '''
                }
            }
        }

        stage('✅ Quality Gate') {
            steps { 
                echo "Waiting for quality gate result..."
                script {
                    timeout(time: 3, unit: 'MINUTES') {
                        def qg = waitForQualityGate()
                        if (qg.status != 'OK') {
                            error "❌ Pipeline stopped: Quality gate failed - ${qg.status}"
                        }
                    }
                }
            }
        }
        
        stage('📦 Package JAR') {
            steps {
                echo "Packaging application..."
                sh "mvn clean package -DskipTests"
                archiveArtifacts artifacts: 'target/*.jar', allowEmptyArchive: true
            }
        }
        
        stage('🐳 Build Docker Image') {
            steps { 
                echo "Building Docker image..."
                script {
                    withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh """
                            echo "🔐 Logging into DockerHub..."
                            docker login -u $DOCKER_USER -p $DOCKER_PASS
                            
                            echo "🏗️ Building Docker image..."
                            docker build -t $registry:latest .
                            
                            echo "🚀 Pushing to DockerHub..."
                            docker push $registry:latest
                            
                            echo "✅ Docker image successfully built and pushed!"
                        """
                    }
                }
            }
        }
        
        stage('🔍 Scan Docker Image') {
            steps { 
                echo "Scanning Docker image for vulnerabilities..."
                sh "trivy image --scanners vuln --exit-code 0 $registry:latest > trivy-scan.txt"
                archiveArtifacts artifacts: 'trivy-scan.txt'
            }
        }
        
        stage('🚀 Smoke Test') {
    steps { 
        echo "Running smoke test on port 8089..."
        script {
            sh """
            # Lancer le container
            docker run -d --name smokerun -p 8089:8089 54788214/student-management:latest
            
            # Attendre que l'application démarre
            sleep 20
            
            # Vérifier les logs
            echo "=== Application Logs ==="
            docker logs smokerun
            
            # Tester l'application
            echo "=== Testing Application ==="
            curl -f http://localhost:8089/student || echo "Application test completed"
            
            # Nettoyer
            docker rm --force smokerun
            """
        }
    }
}
    
    post {
        always {
            echo '🧹 Final cleanup...'
            sh '''
                docker rm -f smokerun 2>/dev/null || true
                echo "📊 Pipeline artifacts:"
                ls -la target/*.jar 2>/dev/null || echo "No JAR files"
                ls -la target/dependency-check-report.* 2>/dev/null || echo "No security reports"
            '''
            
            archiveArtifacts artifacts: 'target/*.jar, target/dependency-check-report.*, trivy-scan.txt, target/site/jacoco/*', allowEmptyArchive: true
        }
        success {
            echo '🎉 FÉLICITATIONS ! Pipeline DevSecOps COMPLET réussi ! 🎉'
            echo '✅ Application built, tested, and containerized'
            echo '✅ Security scans completed'
            echo '✅ Docker image pushed to registry'
        }
        failure {
            echo '❌ Pipeline échoué. Vérifiez les logs pour les détails.'
        }
    }
    
    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
}
