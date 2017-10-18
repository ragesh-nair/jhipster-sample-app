pipeline {
  agent none
  options {
    skipDefaultCheckout()
  }

  environment {
    REL_VERSION = "${BRANCH_NAME.contains('release-') ? BRANCH_NAME.drop(BRANCH_NAME.lastIndexOf('-')+1) + '.' + BUILD_NUMBER : 'SNAPSHOT-' + BUILD_NUMBER}"
    IMAGE = "tech-talk/jhipster-sample:$REL_VERSION"
  }
  stages {
    stage ('Checkout') {
      agent any
      steps {
        deleteDir ()
        checkout  scm: [$class: 'GitSCM', branches: [[name: 'master']], userRemoteConfigs: [[ url: 'https://github.com/ragesh-nair/jhipster-sample-app.git']]]
        stash(name: 'ws', includes: '**', excludes: '**/.git/**')
      }

    }
    stage('Build Backend') {
      agent {
        docker {
          image 'rwasp-docker.prod.jp.local/tech-talk/rwasp-mvn3:1.0.1'
          args '-v $HOME/.m2:/home/jenkins/.m2'
        }
      }
      steps {
        unstash 'ws'
        sh 'mvn -B -DskipTests=true clean compile package'
        stash name: 'war', includes: 'target/**/*'
      }
    }
    stage('Test Backend') {
      agent {
        docker {
		        image 'rwasp-docker.prod.jp.local/tech-talk/rwasp-mvn3:1.0.1'
            args '-v $HOME/.m2:/home/jenkins/.m2'
        }
      }
      steps {
        unstash 'ws'
        unstash 'war'
        sh 'mvn -B test findbugs:findbugs'
      }
      post {
        success {
          junit '**/surefire-reports/**/*.xml'
          findbugs pattern: 'target/**/findbugsXml.xml', unstableNewAll: '0' //unstableTotalAll: '0'
        }
        unstable {
          junit '**/surefire-reports/**/*.xml'
          findbugs pattern: 'target/**/findbugsXml.xml', unstableNewAll: '0' //unstableTotalAll: '0'
        }
      }
    }
    stage ('BuildDocker') {
      agent any
      steps {
        sh 'cp target/jhipster-sample-application-*.war docker/'
        script {
          rwasp.push (docker.build("$IMAGE", 'docker'))
        }
      }
    }
    stage('Test More') {
      parallel {
        stage('Frontend') {
          when {
            anyOf {
              branch "master"
              branch "release-*"
              changeset "src/main/webapp/**/*"
            }
          }
          agent {
            dockerfile {
              args "-v /tmp:/tmp"
              dir "docker/gulp"
            }
          }
          steps {
            unstash 'ws'
            unstash 'war'
            sh '. target/scripts/frontEndTests.sh'
          }
        }
        stage('Performance') {
          when {
            anyOf {
              branch "master"
              branch "release-*"
            }
          }
          agent any
          steps {
            deleteDir ()
            unstash 'ws'
            unstash 'war'
            script {
              //docker.image("$IMAGE").withRun() { c ->
              docker.image("rwasp-docker.prod.jp.local/$IMAGE").withRun() { c ->
                docker.image('rwasp-docker.prod.jp.local/tech-talk/rwasp-mvn3:1.0.1').inside("--link ${c.id}:app -v \$HOME/.m2:/home/jenkins/.m2") {
                  sh 'unset http_proxy; unset https_proxy; mvn -B gatling:execute -DbaseURL=http://app:8080'
                  gatlingArchive()
                }
              }
            }
          }
        }
      }
    }
    stage('Deploy to Staging') {
      agent any
      environment {
        STAGING_AUTH = credentials('staging')
      }
      when {
        anyOf {
          branch "master"
          branch "release-*"
        }
      }
      steps {
        unstash 'war'
        unstash 'ws'
        script {
          Map config = [
          ENV : 'stg-rwasp',
          IMAGE : "$IMAGE",
          Deploy_LOCAL_ENV_CONF : 'rwasp/stg-rwasp/env.json',
          Deploy_LOCAL_RUN_CONF : 'rwasp/stg-rwasp/runtime.conf',
          Switch_DOMAIN : 'tech-talk-jhipster.rwasp-stg.hnd2.bdd.local'
          ]
          rwaspDeployV2 (config)
          rwaspSwitch (config)
        }
      }
      //Post: Send notifications; hipchat, slack, send email etc.
    }
    stage('Archive') {
      agent any
      when {
        not {
          anyOf {
            branch "master"
            branch "release-*"
          }
        }
      }
      steps {
        deleteDir ()
        unstash 'war'
        archiveArtifacts artifacts: 'target/**/*.war', fingerprint: true, allowEmptyArchive: true
      }
    }
    stage('Deploy to Production') {
      agent none
      environment {
        PROD_AUTH = credentials('production')
      }
      when {
        branch "release-*"
      }
      steps {
        timeout(15) {
          input message: 'Deploy to production?', ok: 'Fire zee missiles!'
          node("production") {
            deleteDir ()
            unstash 'war'
            sh '. target/scripts/deploy.sh production -v $REL_VERSION -u $PROD_AUTH_USR -p $PROD_AUTH_PSW'
          }
        }
      }
    }
  }//Stages
  post {
    always {
      script {
        hipchatNotify.completed "RMSg Test", "RMSg_Test", currentBuild.result
      }
    }
    success {
      echo "Do stuffs on success"
    }
    failure {
      echo "Do stuffs on failure"
    }
  }
} //Pipeline
