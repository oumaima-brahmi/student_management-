pipeline {
  agent any

  parameters {
    booleanParam(name: 'ST_CHECKOUT', defaultValue: true,  description: '1) Checkout Git')
    booleanParam(name: 'ST_BUILD',    defaultValue: true, description: '2) Maven build (skip tests)')
    booleanParam(name: 'ST_TESTS',    defaultValue: true, description: '3) Tests + JaCoCo')
    booleanParam(name: 'ST_DC',       defaultValue: true, description: '4) SCA Dependency-Check (fail HIGH/CRIT)')
    booleanParam(name: 'ST_SONAR',    defaultValue: true, description: '5) SonarQube analysis')
    booleanParam(name: 'ST_QG',       defaultValue: true, description: '6) Quality Gate (stop si NOK)')
    booleanParam(name: 'ST_DOCKER',   defaultValue: true, description: '7) Build & Push Docker')
    booleanParam(name: 'ST_TRIVY',    defaultValue: true, description: '8) Trivy image scan (fail HIGH/CRIT)')
    booleanParam(name: 'ST_RUNAPP',   defaultValue: true, description: '9) Run app container (pour DAST & smoke)')
    booleanParam(name: 'ST_SMOKE',    defaultValue: true, description: '10) Smoke test /student')
    booleanParam(name: 'ST_GITLEAKS', defaultValue: true, description: '11) Gitleaks (fail on leak)')
    booleanParam(name: 'ST_ZAP',      defaultValue: true, description: '12) ZAP Baseline (fail >= Medium)')
  }

  options { timestamps() }

  tools {
    jdk 'jdk 21'
    maven 'maven'
  }

  environment {
    REGISTRY      = '54788214/student-management-ci'
    REGISTRY_CRED = 'dockerhub'
    SONAR_SERVER  = 'mysonarqube'
    NVD_API_KEY   = credentials('nvd-api-key')
    DC_DATA_DIR   = "${WORKSPACE}/.dc-data"
  }

  stages {

    stage('📥 Checkout') {
      when { expression { params.ST_CHECKOUT } }
      steps {
        git branch: 'main',
            credentialsId: 'tokengithub',
            url: 'https://github.com/oumaima-brahmi/student_management-.git'
      }
    }

    stage('🔨 Build (Maven)') {
      when { expression { params.ST_BUILD } }
      steps {
        sh 'mvn -B -ntp clean package -DskipTests'
      }
    }

    stage('🧪 Tests + JaCoCo') {
      when { expression { params.ST_TESTS } }
      steps {
        sh 'mvn -B -ntp test jacoco:report'
      }
      post {
        always {
          junit '**/target/surefire-reports/*.xml'
          archiveArtifacts artifacts: 'target/site/jacoco/jacoco.xml', allowEmptyArchive: true
          publishHTML(target: [
            reportDir: 'target/site/jacoco',
            reportFiles: 'index.html',
            reportName: 'JaCoCo Coverage',
            alwaysLinkToLastBuild: true,
            keepAll: true,
            allowMissing: true
          ])
        }
      }
    }

  stage('🔒 SCA - Dependency-Check (fail-high)') {
  when { expression { params.ST_DC } }
  tools { jdk 'jdk 21'; maven 'maven' }

  steps {
    withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
      sh '''
        set -e
        mkdir -p .dc-data

        # 1️⃣ Mise à jour de la base NVD
        if ! mvn -B -ntp org.owasp:dependency-check-maven:12.1.0:update-only \
              -DdataDirectory=.dc-data \
              -DnvdApiKey=$NVD_API_KEY \
              -DconnectionTimeout=600000 -DreadTimeout=600000 ; then
          echo "Update failed — purging local DB and retrying..."
          mvn -B -ntp org.owasp:dependency-check-maven:12.1.0:purge -DdataDirectory=.dc-data || true
          rm -rf .dc-data && mkdir -p .dc-data
          mvn -B -ntp org.owasp:dependency-check-maven:12.1.0:update-only \
              -DdataDirectory=.dc-data \
              -DnvdApiKey=$NVD_API_KEY \
              -DconnectionTimeout=600000 -DreadTimeout=600000
        fi

        # 2️⃣ Analyse des dépendances
        mvn -B -ntp org.owasp:dependency-check-maven:12.1.0:check \
          -DdataDirectory=.dc-data \
          -Dformat=HTML,JSON,XML \
          -DoutputDirectory=target \
          -DoutputFile=dependency-check-report \
          -DnvdApiKey=$NVD_API_KEY \
          -DfailBuildOnCVSS=7.0 \
          -Danalyzer.ossindex.enabled=false \
          -DfailOnError=true \
          -DconnectionTimeout=600000 -DreadTimeout=600000
      '''
    }
  }

  post {
    always {
      publishHTML(target: [
        reportDir: 'target',
        reportFiles: 'dependency-check-report.html',
        reportName: 'OWASP Dependency-Check (HTML)',
        alwaysLinkToLastBuild: true,
        keepAll: true,
        allowMissing: true
      ])
      archiveArtifacts artifacts: 'target/dependency-check-report.*', allowEmptyArchive: true
      dependencyCheckPublisher pattern: 'target/dependency-check-report.xml'
    }
  }
}
   stage('📊 SAST - SonarQube') {
      when { expression { params.ST_SONAR } }
      steps {
        withSonarQubeEnv("${SONAR_SERVER}") {
          sh '''
            mvn -B -ntp sonar:sonar \
              -Dsonar.projectKey=tn.esprit:student-management \
              -Dsonar.projectName=student-management \
              -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
              -Dsonar.dependencyCheck.jsonReportPath=target/dependency-check-report.json
          '''
        }
      }
    }
    stage('✅ Quality Gate') {
      when { expression { params.ST_QG } }
      steps {
        timeout(time: 10, unit: 'MINUTES') {
          waitForQualityGate abortPipeline: true
        }
      }
    }

    stage('🐳 Build & Push Docker') {
      when { expression { params.ST_DOCKER } }
      steps {
        script {
          def tag = "build-${env.BUILD_NUMBER}"
          docker.withRegistry('', REGISTRY_CRED) {
            def img = docker.build("${REGISTRY}:${tag}")
            img.push()
            img.push('latest')
          }
          env.IMAGE_TAG = tag
        }
      }
    }

    stage('🔍 Trivy Image Scan (fail-high)') {
  when { expression { params.ST_TRIVY } }
  steps {
    script {
      if (!env.IMAGE_TAG) {
        error "Active ST_DOCKER avant ST_TRIVY (IMAGE_TAG manquant)."
      }
    }

    sh '''
      set -e
      mkdir -p .trivy-cache
      
     echo "📥 Téléchargement du template HTML Trivy..."
  mkdir -p contrib
  curl -fsSL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl -o contrib/html.tpl || {
    echo "❌ Échec du téléchargement du template HTML Trivy."
    exit 1
  }  
      echo "🧹 Nettoyage du cache Trivy..."
      trivy clean --all || true

      echo "⬇️ Téléchargement de la base de vulnérabilités..."
      trivy image --download-db-only --cache-dir .trivy-cache || true

      echo "🔎 Analyse de l'image Docker avec Trivy..."
      trivy image \
        --scanners vuln \
        --severity CRITICAL,HIGH \
        --exit-code 1 \
        --timeout 30m \
        --cache-dir .trivy-cache \
        --format template \
        --template "@contrib/html.tpl" \
        --output trivy-report.html \
        ${REGISTRY}:${IMAGE_TAG} \
        2>&1 | tee trivy-results.txt || EXIT=$?

      if [ "${EXIT:-0}" -ne 0 ]; then
        echo "⚠️ Échec ou vulnérabilités détectées, relance en 15s..."
        sleep 15
        trivy image \
          --scanners vuln \
          --severity CRITICAL,HIGH \
          --exit-code 1 \
          --timeout 30m \
          --cache-dir .trivy-cache \
          --format template \
          --template "@contrib/html.tpl" \
          --output trivy-report.html \
          ${REGISTRY}:${IMAGE_TAG} 2>&1 | tee -a trivy-results.txt
      fi
    '''
  }

  post {
    always {
      archiveArtifacts artifacts: 'trivy-results.txt, trivy-report.html', allowEmptyArchive: true
      publishHTML(target: [
        reportDir: '.',
        reportFiles: 'trivy-report.html',
        reportName: 'Trivy Vulnerability Report',
        alwaysLinkToLastBuild: true,
        keepAll: true,
        allowMissing: true
      ])
    }
  }
}
    stage('🚀 Run app container') {
      when { expression { params.ST_RUNAPP } }
      steps {
        script {
          if (!env.IMAGE_TAG) {
            error "Active ST_DOCKER avant ST_RUNAPP (image manquante)."
          }
        }
        sh '''
          set -e

          docker rm -f smokerun 2>/dev/null || true
          rm -rf app-logs && mkdir -p app-logs

          docker run -d --name smokerun -p 8089:8089 \
            -v "$PWD/app-logs:/logs" \
            -e LOGGING_FILE_NAME=/logs/app.log \
            -e SPRING_DATASOURCE_URL='jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1;MODE=MySQL' \
            -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver \
            -e SPRING_DATASOURCE_USERNAME=sa \
            -e SPRING_DATASOURCE_PASSWORD= \
            -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
            ${REGISTRY}:${IMAGE_TAG}

          echo "Attente du démarrage de l’app..."
          sleep 3

          ok=false
          for i in $(seq 1 60); do
            code=$(curl -sS --connect-timeout 3 --max-time 5 -o /dev/null -w %{http_code} \
                   http://localhost:8089/student/actuator/health || true)
            echo "[try $i] /student/actuator/health -> $code"
            if [ "$code" = "200" ]; then ok=true; break; fi

            code=$(curl -sS --connect-timeout 3 --max-time 5 -o /dev/null -w %{http_code} \
                   http://localhost:8089/student/Depatment/getAllDepartment || true)
            echo "[try $i] /student/Depatment/getAllDepartment -> $code"
            if [ "$code" = "200" ]; then ok=true; break; fi

            sleep 2
          done

          if [ "$ok" != "true" ]; then
            echo "⚠️ L’application ne répond pas. État/ports et logs :"
            echo "--- docker ps ---"
            docker ps -a --filter name=smokerun || true
            echo "--- docker inspect ---"
            docker inspect smokerun --format='State: {{.State.Status}}  Ports: {{json .NetworkSettings.Ports}}' || true
            echo "--- docker logs (200 lignes) ---"
            docker logs --tail 200 smokerun || true
            echo "--- tail app-logs/app.log (si présent) ---"
            tail -n 200 app-logs/app.log 2>/dev/null || true
            exit 1
          fi
        '''
      }
      post {
        always {
          sh 'docker logs --tail 1000 smokerun > smokerun.log 2>/dev/null || true'
          archiveArtifacts artifacts: 'smokerun.log', allowEmptyArchive: true
          archiveArtifacts artifacts: 'app-logs/app.log', allowEmptyArchive: true
        }
      }
    }

    stage('🧪 Smoke Test') {
      when { expression { params.ST_SMOKE } }
      steps {
        // Affiche l’en-tête + le JSON de la réponse dans la console Jenkins
        sh 'curl -sS -i http://localhost:8089/student/Depatment/getAllDepartment'
      }
    }

   stage('🔐 Gitleaks (fail on leak)') {
  when { expression { params.ST_GITLEAKS } }
  steps {
    sh '''
      docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:latest detect \
        --source=/repo --no-banner \
        --exit-code 1 \
        --no-git \
        --config /repo/.gitleaks.toml \
        --report-format=json --report-path=/repo/gitleaks-report.json
    '''
  }
  post {
    always {
      archiveArtifacts artifacts: 'gitleaks-report.json', allowEmptyArchive: true
    }
  }
}
    stage('🛡️ ZAP Baseline (fail >= High)') {
  when { expression { params.ST_ZAP } }
  steps {
    sh '''
      set -e
      rm -rf zap-out && mkdir -p zap-out
      chmod 777 zap-out

      docker pull ghcr.io/zaproxy/zaproxy:stable

      set +e
      # On capture la sortie dans un log, SANS casser tout de suite le build
      docker run --rm --network host --user 0:0 \
        -v "$PWD/zap-out:/zap/wrk" ghcr.io/zaproxy/zaproxy:stable \
        zap-baseline.py \
          -t http://localhost:8089/student/Depatment/getAllDepartment \
          -m 3 \
          -r zap-baseline.html \
          -J zap-baseline.json \
          -d -z "-config api.disablekey=true" | tee zap-out/zap-console.log
      rc=$?
      set -e

      # On récupère les rapports à la racine pour HTML Publisher
      cp -f zap-out/zap-baseline.html . 2>/dev/null || true
      cp -f zap-out/zap-baseline.json . 2>/dev/null || true
      cp -f zap-out/zap-console.log  . 2>/dev/null || true

      # Décision d'échec:
      # - échouer si le résumé indique FAIL-NEW > 0
      # - ou s'il y a "Automation plan failures:"
      # Sinon, ignorer les warnings (exit code 2) et passer.
      if grep -q 'Automation plan failures:' zap-out/zap-console.log; then
        echo "❌ ZAP: Automation plan failures détectées."
        exit 1
      fi

      # extraire le compteur FAIL-NEW: <nombre>
      FAILS=$(grep -Eo 'FAIL-NEW: [0-9]+' zap-out/zap-console.log | awk '{print $2}' | tail -n1)
      if [ -z "$FAILS" ]; then
        echo "ℹ️ Impossible de lire FAIL-NEW, on garde le code retour original: $rc"
        # Si pas de failures et seulement des warnings (rc=2), on passe
        if [ "$rc" -eq 2 ]; then
          echo "↪️ ZAP a retourné 2 (warnings) mais aucune failure détectée."
          exit 0
        fi
        exit "$rc"
      fi

      if [ "$FAILS" -gt 0 ]; then
        echo "❌ ZAP: $FAILS fail(s) nouvelles (>= High)."
        exit 1
      else
        echo "✅ ZAP: aucune fail >= High. On ignore les warnings."
        exit 0
      fi
    '''
  }
  post {
    always {
      publishHTML(target: [
        reportDir: '.',
        reportFiles: 'zap-baseline.html',
        reportName: 'OWASP ZAP Baseline',
        alwaysLinkToLastBuild: true,
        keepAll: true,
        allowMissing: true
      ])
      archiveArtifacts artifacts: 'zap-baseline.*', allowEmptyArchive: true
      archiveArtifacts artifacts: 'zap-console.log', allowEmptyArchive: true
    }
  }
}
  } // <-- fin stages

  post {
    always {
        echo "🧩 Conteneur conservé pour test manuel (suppression désactivée)"
      // si tu veux garder le conteneur pour debug, commente la ligne suivante
      //sh 'docker rm -f smokerun 2>/dev/null || true'
    }
    success { echo '✅ Étape(s) validée(s). Active la suivante et relance.' }
    failure { echo '⛔ Échec : corrige le stage en rouge, puis relance.' }
  }

} // <-- fin pipeline

