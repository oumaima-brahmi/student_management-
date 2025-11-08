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
                sh "mvn clean package -DskipTests"
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
                    // ESSAI 1 : Avec clé API
                    try {
                        sh """
                            mvn dependency-check:check \
                            -DnvdApiKey=${NVD_API_KEY} \
                            -DautoUpdate=true \
                            -DfailBuildOnAnyVulnerability=false
                        """
                        echo "✅ Security scan successful with NVD API!"
                    } catch (Exception e) {
                        // ESSAI 2 : Mode hors ligne
                        echo "⚠️  Online scan failed, using offline mode..."
                        sh """
                            mvn dependency-check:check \
                            -DautoUpdate=false \
                            -DfailBuildOnAnyVulnerability=false \
                            -DfailOnError=false
                        """
                        echo "✅ Security scan completed in offline mode"
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
                }
            }
        }

        stage('⚡ Security Scan - SAST') {
            steps { 
                echo "Static Application Security Testing with SonarQube..."
                withSonarQubeEnv('sonarqube') {
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
        
        stage('🐳 Build Docker Image') {
            steps { 
                echo "Building Docker image..."
                script {
                    docker.withRegistry( '', registryCredential ) { 
                        myImage = docker.build registry + ":latest"
                        myImage.push()
                    }
                }
            }
        }
        
        stage('🔍 Scan Docker Image') {
    steps { 
        echo "Scanning Docker image with optimized Trivy..."
        sh """
            # Nettoyer le cache avant
            trivy --clear-cache || true
            
            # Scanner seulement les vulnérabilités critiques (moins de données)
            trivy image --scanners vuln --severity CRITICAL,HIGH 54788214/student-management:latest > trivy-results.txt
        """
        archiveArtifacts artifacts: 'trivy-results.txt'
    }
}
        
        stage('🚀 Smoke Test') {
            steps { 
                echo "Running smoke test..."
                script {
                    sh "docker run -d --name smokerun -p 8089:8089 54788214/student-management:latest"
                    sh "sleep 30; curl -f http://localhost:8089/student || exit 1"
                    sh "docker rm --force smokerun"
                }
            }
        }
    }
    
    post {
        always {
            echo '🧹 Cleaning up...'
            sh 'docker rm --force smokerun 2>/dev/null || true'
        }
        success {
            echo '🎉 FÉLICITATIONS ! Pipeline DevSecOps RÉUSSI ! 🎉'
            echo '✅ Tous les tests de sécurité sont passés !'
        }
        failure {
            echo '❌ Pipeline échoué. Vérifiez les logs pour les détails.'
        }
    }
}
