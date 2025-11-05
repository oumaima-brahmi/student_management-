pipeline {
  agent any
  environment { 
        registry = "54788214/student-management"
        registryCredential = 'dockerhub'
        MAVEN_OPTS = '-DconnectionTimeout=180000 -DreadTimeout=180000'
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
        sh "mvn clean compile -DconnectionTimeout=180000 -DreadTimeout=180000"
      }
    }
    
    stage('🧪 Run Tests') {
      steps {
        echo "Running tests..."
        sh "mvn test -DconnectionTimeout=180000 -DreadTimeout=180000"
      }
    }
    
    stage('📊 Code Coverage') {
      steps {
        echo "Generating code coverage report..."
        sh "mvn jacoco:report -DconnectionTimeout=180000 -DreadTimeout=180000"
      }
    }
    
    stage('🔒 Security Scan - SCA') {
      steps { 
        echo "Scanning dependencies for vulnerabilities..."
        script {
          // Premier essai avec timeout augmenté
          try {
            sh """
              mvn org.owasp:dependency-check-maven:check \
                -DconnectionTimeout=180000 \
                -DreadTimeout=180000 \
                -DfailBuildOnAnyVulnerability=false \
                -DcveValidForHours=24
            """
          } catch (Exception e) {
            echo "⚠️  First scan failed, retrying with longer timeout..."
            // Second essai avec timeout encore plus long
            sh """
              mvn org.owasp:dependency-check-maven:check \
                -DconnectionTimeout=300000 \
                -DreadTimeout=300000 \
                -DfailBuildOnAnyVulnerability=false \
                -DcveValidForHours=48
            """
          }
        }
      }
      post {
        always {
          // Publication du rapport OWASP
          dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
          
          // Archivage des rapports
          archiveArtifacts artifacts: 'target/dependency-check-report.*', allowEmptyArchive: true
          publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: 'target',
            reportFiles: 'dependency-check-report.html',
            reportName: 'OWASP Dependency Check Report'
          ])
        }
      }
    }
    
    stage('⚡ Security Scan - SAST') {
      steps { 
        echo "Static Application Security Testing with SonarQube..."
        withSonarQubeEnv('mysonarqube') {
            sh """
            mvn sonar:sonar \
            -Dsonar.projectName=student-management \
            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
            -Dsonar.dependencyCheck.jsonReportPath=target/dependency-check-report.json \
            -Dsonar.dependencyCheck.htmlReportPath=target/dependency-check-report.html \
            -DconnectionTimeout=180000 \
            -DreadTimeout=180000
            """
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
        sh "trivy image --scanners vuln --exit-code 0 --format table 54788214/student-management:latest > trivy-results.txt"
        sh "trivy image --scanners vuln --exit-code 1 --severity CRITICAL,HIGH 54788214/student-management:latest || true"
        archiveArtifacts artifacts: 'trivy-results.txt'
      }
    }
    
    stage('🚀 Smoke Test') {
      steps { 
        echo "Running smoke test..."
        script {
          try {
            sh "docker run -d --name smokerun -p 8080:8080 54788214/student-management:latest"
            sh "sleep 30; curl -f http://localhost:8080/actuator/health || curl -f http://localhost:8080 || exit 1"
          } finally {
            sh "docker rm --force smokerun || true"
          }
        }
      }
    }
    
    stage('📦 Archive Reports') {
      steps {
        echo "Archiving all test and security reports..."
        archiveArtifacts artifacts: 'target/*.jar, target/*.war, target/site/**/*, target/*.txt', allowEmptyArchive: true
        junit 'target/surefire-reports/*.xml'
      }
    }
  }
  
  post {
    always {
      echo '🧹 Cleaning up...'
      sh 'docker rm --force smokerun 2>/dev/null || true'
      sh 'docker system prune -f || true'
      
      // Publication des rapports même en cas d'échec
      publishHTML([
        allowMissing: true,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: 'target/site/jacoco',
        reportFiles: 'index.html',
        reportName: 'JaCoCo Code Coverage Report'
      ])
    }
    
    success {
      echo '🎉 FÉLICITATIONS ! Pipeline DevSecOps RÉUSSI avec GitHub ! 🎉'
      emailext (
        subject: "✅ SUCCÈS: Pipeline DevSecOps - ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
        body: """
        Le pipeline DevSecOps a été exécuté avec succès !
        
        Détails:
        - Job: ${env.JOB_NAME}
        - Build: ${env.BUILD_NUMBER}
        - Commit: ${env.GIT_COMMIT}
        - Rapport OWASP: ${env.BUILD_URL}/OWASP_20Dependency_20Check_20Report/
        - Rapport Couverture: ${env.BUILD_URL}/JaCoCo_20Code_20Coverage_20Report/
        
        Félicitations ! 🎉
        """,
        to: "${env.CHANGE_AUTHOR_EMAIL ?: 'team@example.com'}"
      )
    }
    
    failure {
      echo '❌ Pipeline échoué. Vérifiez les logs pour les détails.'
      emailext (
        subject: "❌ ÉCHEC: Pipeline DevSecOps - ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
        body: """
        Le pipeline DevSecOps a échoué.
        
        Détails:
        - Job: ${env.JOB_NAME} 
        - Build: ${env.BUILD_NUMBER}
        - Commit: ${env.GIT_COMMIT}
        - URL du build: ${env.BUILD_URL}
        
        Veuillez vérifier les logs pour identifier la cause de l'échec.
        """,
        to: "${env.CHANGE_AUTHOR_EMAIL ?: 'team@example.com'}"
      )
    }
    
    unstable {
      echo '⚠️  Pipeline instable. Qualité du code insuffisante.'
    }
  }
  
  options {
    timeout(time: 60, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '10'))
    disableConcurrentBuilds()
    retry(2)
  }
  
  triggers {
    pollSCM('H/15 * * * *')
  }
}
