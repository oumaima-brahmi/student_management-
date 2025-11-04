pipeline {
  agent any
  environment { 
        registry = "54788214/student-management"
        registryCredential = 'dockerhub'
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
        sh "mvn org.owasp:dependency-check-maven:check"
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
            -Dsonar.dependencyCheck.jsonReportPath=target/dependency-check-report.json \
            -Dsonar.dependencyCheck.htmlReportPath=target/dependency-check-report.html
            '''
        }
      }
    }
    stage('✅ Quality Gate') {
      steps { 
        echo "Waiting for quality gate result..."
        script {
          timeout(time: 2, unit: 'MINUTES') {
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
        echo "Scanning Docker image for vulnerabilities..."
        sh "trivy image --scanners vuln 54788214/student-management:latest > trivy-results.txt"
        archiveArtifacts artifacts: 'trivy-results.txt'
      }
    }
    stage('🚀 Smoke Test') {
      steps { 
        echo "Running smoke test..."
        sh "docker run -d --name smokerun -p 8080:8080 54788214/student-management:latest"
        sh "sleep 30; curl -f http://localhost:8080 || exit 1"
        sh "docker rm --force smokerun"
      }
    }
  }
  post {
    always {
      echo '🧹 Cleaning up...'
      sh 'docker rm --force smokerun 2>/dev/null || true'
    }
    success {
      echo '🎉 FÉLICITATIONS ! Pipeline DevSecOps RÉUSSI avec GitHub ! 🎉'
    }
    failure {
      echo '❌ Pipeline échoué. Vérifiez les logs pour les détails.'
    }
  }
}
EOF
