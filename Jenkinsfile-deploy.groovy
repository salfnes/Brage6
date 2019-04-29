pipeline {
    agent any

    tools {
        maven 'Maven 3.6.1'
    }


//    parameters {
//        choice(name: 'Destination', choices: ['brage-utvikle.bibsys.no', 'brage-test.bibsys.no']
//                , description: 'Where do you want to deploy to?')
//    }

    environment {
        VERSION = "${env.BRANCH_NAME}".replaceAll('/', '_').toLowerCase()
       // TARGET_HOST = "${params.Destination}"
        TARGET_HOST = "brage-utvikle.bibsys.no"
        ENV_FOLDER = "brage6_environment"
        //The rest of the parameters is imported from env.properties"
    }


    stages {
//        stage('Confirm deploy') {
//            steps {
//                script {
//                    try {
//                        timeout(activity: true, time: 120, unit: 'SECONDS') {
//                            input(message: "Deploy branch: $VERSION to server: $TARGET_HOST?")
//                        }
//                    } catch (err) {
//                        println("Release aborted")
//                        throw err
//                    }
//                }
//                println("Deploying branch: $VERSION to server: $TARGET_HOST")
//            }
//        }

        stage('Checkout') {
            steps {

                println("Running build #${env.BUILD_ID} of job ${env.JOB_NAME}, git branch: ${env.BRANCH_NAME}" as java.lang.Object)
                script {
                    brageVars = checkout scm
                    dir("${ENV_FOLDER}") {
                        //configVars = checkout scm
                        git url: 'git@github.com:BIBSYSDEV/Brage6-environment.git'
                    }
                }
            }
        }

        stage('Pre-build scripts') {
            steps {
                sh "less ${ENV_FOLDER}/env/env.properties"
                echo "Fetching environmentvariables for build from : ${ENV_FOLDER}/env/env.properties"
                load "brage6_environment/env/env.properties"
                echo "TARGET_FOLDER: ${TARGET_FOLDER}"
                echo "COMPRESSED_INSTALLER_FILE: ${COMPRESSED_INSTALLER_FILE}"
                echo "INSTALLER_SCRIPT: ${INSTALLER_SCRIPT}"

                echo "copying local.cfg from environment-project in brage6"
                sh "cp ${ENV_FOLDER}/env/local_UTVIKLE.cfg dspace/config/local.cfg"
            }
        }


        stage('Maven Build') {
            steps {
                echo "Pretending to build with maven"
//                sh 'mkdir -p dspace/target'
//                sh 'mkdir -p dspace/target/dspace-installer'
//                sh 'touch dspace/target/dspace-installer/tullefil.txt'
//                sh 'ls dspace/target/dspace-installer/'
                sh 'mvn package -Dmirage2.on=true -P !dspace-lni,!dspace-sword,!dspace-jspui,!dspace-rdf'
            }
        }

        stage('Copy files to brage-server, running installer-script') {
            steps {
                echo "Destination environment: ${TARGET_HOST}"
                sh "ssh ${TARGET_HOST} mkdir -p ${TARGET_FOLDER}"

                echo "Compressing dspace/target/dspace-installer to ${COMPRESSED_INSTALLER_FILE}"
                sh "tar -zcf ${COMPRESSED_INSTALLER_FILE} -C dspace/target dspace-installer"

                echo "Transferring ${COMPRESSED_INSTALLER_FILE} to ${TARGET_HOST}:${TARGET_FOLDER}"
                sh "scp ${COMPRESSED_INSTALLER_FILE} ${TARGET_HOST}:${TARGET_FOLDER}/"

                echo "Transferring ${ENV_FOLDER}/scripts to ${TARGET_HOST}:${TARGET_FOLDER}"
                sh "scp -r ${ENV_FOLDER}/scripts ${TARGET_HOST}:${TARGET_FOLDER}/"
                sh "scp -r ${ENV_FOLDER}/scripts/${INSTALLER_SCRIPT} ${TARGET_HOST}:${TARGET_FOLDER}/"

                echo "Executing ${TARGET_FOLDER}/installer.sh on ${TARGET_HOST}"
                sh "ssh ${TARGET_HOST} 'source ~/.profile; sh ${TARGET_FOLDER}/${INSTALLER_SCRIPT};'"
            }
        }

        stage('Cleanup') {
            steps {
                cleanWs()
            }
        }
    }

    post {
        failure {
            script {
                def message = "${currentBuild.fullDisplayName} - Failure after ${currentBuild.durationString.replaceFirst(" and counting", "")}"
                emailext(
                        subject: "FAILURE: ${currentBuild.fullDisplayName}",
                        body: "${message}\n Open: ${env.BUILD_URL}",
                        to: 'pcb@unit.no',
                        attachlog: true,
                        compresslog: true,
                        recipientProviders: [[$class: 'CulpritsRecipientProvider']]
                )

            }
        }
    }
}

