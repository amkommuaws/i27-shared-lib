import com.i27academy.builds.Docker
import com.i27academy.k8s.K8s

library ("com.i27academy.slb")

def call(Map pipelineParams) {
    Docker docker = new Docker(this)
    K8s k8s = new K8s(this)
    pipeline {
        agent {
            label 'k8s-slave'
        }
        parameters {
            choice(name: 'buildOnly',
                choices: 'no\nyes',           /// \n for next line
                description: 'This will only build the application'
            )
            choice(name: 'scanOnly',
                choices: 'no\nyes',           /// \n for next line
                description: 'This will only scan the application'
            )
            choice(name: 'dockerPush',
                choices: 'no\nyes',           /// \n for next line
                description: 'This will build the app, docker push' 
            )
            choice(name: 'deploytoDev',
                choices: 'no\nyes',           /// \n for next line
                description: 'This will deploy the app to Dev env' 
            )
            choice(name: 'deploytoTest',
                choices: 'no\nyes',           /// \n for next line
                description: 'This will deploy the app to Test env' 
            )
            choice(name: 'deploytoStage',
                choices: 'no\nyes',           /// \n for next line
                description: 'This will deploy the app to Stage env' 
            )
            choice(name: 'deploytoProd',
                choices: 'no\nyes',           /// \n for next line
                description: 'This will deploy the app to Prod env' 
            )
        }
        environment {
            APPLICATION_NAME = "${pipelineParams.appName}"
            //APPLICATION_NAME = "eureka"
            POM_VERSION = readMavenPom().getVersion()
            POM_PACKAGING = readMavenPom().getPackaging()
            // versioning + packaging
            DOCKER_HUB = "docker.io/amkommuaws123"
            DOCKERHUB_CREDS = credentials("DockerHub_Creds_amkommuaws123")
            SONAR_URL = "http://34.134.238.254:9000"
            SONAR_TOKEN = credentials("Sonar_Creds")
            GKE_DEV_CLUSTER_NAME = "cart-cluster"
            GKE_DEV_ZONE = "us-west1-a"
            GKE_DEV_PROJECT = "i27projects"
            DOCKER_IMAGE_TAG = sh(script: 'git log -1 --pretty=%h', returnStdout:true).trim()
            K8S_DEV_FILE = "k8s_dev.yaml"
            K8S_TST_FILE = "k8s_tst.yaml"
            K8S_STAGE_FILE = "k8s_stg.yaml"
            K8S_PROD_FILE = "k8s_prod.yaml"
            DEV_NAMESPACE =  "cart-dev-ns"
            TST_NAMESPACE =  "cart-tst-ns"
            STG_NAMESPACE =  "cart-stg-ns"
            PROD_NAMESPACE =  "cart-prod-ns"
        }
        tools {
            maven "Maven-3.8.8"
            jdk "JDK-17"
        }
        stages {
            stage('Checkout') {
                steps {
                    println("Chekout: Git clone for i27SharedLib Starting........")
                    k8s.gitClone()
                }
            }
            stage ('Authenticate to Google Cloud GKE') {
                steps {
                    echo "Executing in Google cloud auth stage"
                    script {
                        //gke_cluster_name, gke_zone, gke_project
                        k8s.auth_login("${env.GKE_DEV_CLUSTER_NAME}", "${env.GKE_DEV_ZONE}", "${env.GKE_DEV_PROJECT}")
                    }
                }
            }
            stage ('Build') {
                when {
                    anyOf {
                        expression {
                            params.buildOnly == 'yes'
                            //params.dockerPush == 'yes'
                        }
                    }
                }
                // Application Build happens here
                steps { //For jenkins env variables no need to write env.
                    script {                    
                        //buildApp().call()
                        docker.buildApp("${env.APPLICATION_NAME}")                  
                    }
                }
            }
            stage ('Unit Tests') {
                when {
                    anyOf {
                        expression {
                            params.buildOnly == 'yes'
                            params.dockerPush == 'yes'
                        }
                    }
                }
                steps {
                    echo "Performing Unit tests for ${env.APPLICATION_NAME} application"
                    sh "mvn test"
                }
                post {
                    always {
                        junit "target/surefire-reports/*.xml"
                    }
                }
            }
            stage ('Sonar') {
                when {
                    expression {
                        params.scanOnly == 'yes'       
                    }
                }    
                steps {
                    echo "************ Starting SonarQube with Quality Gates **************"
                    withSonarQubeEnv('SonarQube') { //manage jenkins > System > SonarQube Server
                        sh """
                        mvn clean verify sonar:sonar \
                                -Dsonar.projectKey=i27-eureka \
                                -Dsonar.host.url=${env.SONAR_URL} \
                                -Dsonar.login=${SONAR_TOKEN}
                        """                
                    }
                    timeout (time: 2, unit: 'MINUTES') { //NANOSECONDS, SECONDS, MINUTES, HOURS, DAYS
                    script {
                        waitForQualityGate abortPipeline: true
                    }
                    }   
                }
            }
            /*
            stage ('Docker Format') {
                steps {
                    //Tell me how can i read pom.xml from jenkinsfile
                    echo "Actual Format: ${env.APPLICATION_NAME}-${env.POM_VERSION}-${env.POM_PACKAGING}"
                    // i Need to have below formating
                    // eureka-buildNumber-branchName.packaging
                    echo "Custom Format: ${env.APPLICATION_NAME}-${currentBuild.number}-${BRANCH_NAME}.${env.POM_PACKAGING}"
                }
            }*/
            stage ('Docker Build and Push') {
                when {
                    anyOf {
                        expression {
                            params.dockerPush == 'yes'
                        }
                    }
                }
                steps {
                    script {
                    dockerBuildandPush().call()
                    // docker build -t registryname:tag ./cicd
                    // cp /home/amruthnation27/jenkins/workspace/i27-Eureka_master/target/i27-eureka-0.0.1-SNAPSHOT.jar ./.cicd
                    // workspace means before the target fldr : see below
                    // workspace/target/i27-eureka-0.0.1-snapshot-jar
                    // docker login docker.io -u username -p password
                    // my docker should be like this: amkommuaws/eureka:tag
                    }
                }
            }
            stage ('Deploy to Dev') {
                when {
                    expression {
                        params.deploytoDev == 'yes'
                    }
                }
                steps {
                script {
                    sh "ls -la"
                    sh "ls -la .cicd"
                    //imageValidation().call()
                    def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${env.DOCKER_IMAGE_TAG}"
                    // dockerDeploy('dev', '5761', '8761').call()
                    //k8s.auth_login("${env.GKE_DEV_CLUSTER_NAME}", "${env.GKE_DEV_ZONE}", "${env.GKE_DEV_PROJECT}")
                    //k8s.k8sdeploy("${env.K8S_DEV_FILE}", docker_image, "${env.DEV_NAMESPACE}")
                    //k8s.k8sHelmChartDeploy()
                    echo "********** Deployed to Dev Successfully *************"
                }
                }
            }
            stage ('Deploy to Test') {
                when {
                    expression {
                        params.deploytoTest == 'yes'
                    }
                }
                steps {
                script {
                    imageValidation().call()
                    def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${env.DOCKER_IMAGE_TAG}"
                    echo "************* Entering Test Env *****************"
                    //dockerDeploy('test', '6761', '8761').call()
                    k8s.auth_login("${env.GKE_DEV_CLUSTER_NAME}", "${env.GKE_DEV_ZONE}", "${env.GKE_DEV_PROJECT}")
                    k8s.k8sdeploy("${env.K8S_TST_FILE}", docker_image, "${env.TST_NAMESPACE}")
                    echo "********** Deployed to Test Successfully *************"
                }
                }
            }
            stage ('Deploy to Stage') {
                when {
                    expression {
                        params.deploytoStage == 'yes'
                    }
                }
                steps {
                    script {
                    imageValidation().call()
                    def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${env.DOCKER_IMAGE_TAG}"
                    //dockerDeploy('stage', '7761', '8761').call()
                    k8s.auth_login("${env.GKE_DEV_CLUSTER_NAME}", "${env.GKE_DEV_ZONE}", "${env.GKE_DEV_PROJECT}")
                    k8s.k8sdeploy("${env.K8S_STAGE_FILE}", docker_image, "${env.STG_NAMESPACE}")
                    echo "********** Deployed to Stage Successfully *************"
                }
                }
            }
            stage ('Deploy to Prod') {
                when {
                // deploytoProd ===> yes and branch should be "release/***"
                allOf {
                    anyOf {
                        expression {
                            params.deploytoProd == 'yes'
                        }
                    }
                    anyOf {
                        branch 'release/*'
                        // only tags with v.*.*.* should deploy to prod
                    }
                }
                }
                steps {
                timeout(time: 300, unit: 'SECONDS') { //5mins
                        input message: "Deploying to ${env.APPLICATION_NAME}-prod ???", ok: 'yes', submitter: "krish"
                }
                script {
                    imageValidation().call()
                    def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${env.DOCKER_IMAGE_TAG}"
                    //dockerDeploy('prod', '8761', '8761').call()
                    k8s.auth_login("${env.GKE_DEV_CLUSTER_NAME}", "${env.GKE_DEV_ZONE}", "${env.GKE_DEV_PROJECT}")
                    k8s.k8sdeploy("${env.K8S_PROD_FILE}", docker_image, "${env.PROD_NAMESPACE}")
                    echo "********** Deployed to Prod Successfully *************"
                }
                }
            }
            stage ('CleanWorkSpace') {
                steps {
                    cleanWs()
                }
            }
        }
    }
}
// This Jenkinsfile is for the Eureka Deployment





// This Jenkins file is for Eureka Deployment
// This method will build image and push to registry
def dockerBuildandPush() {
    return {
        echo "************************* Building DOcker Image*************************"
        sh "cp ${workspace}/target/i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} ./.cicd"
        sh "ls -la ./.cicd"
        sh "docker build --force-rm --no-cache --pull --rm=true --build-arg JAR_SOURCE=i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} -t ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${env.DOCKER_IMAGE_TAG} ./.cicd"
        echo "************************* Login to Docker Repo*************************"
        sh "docker login -u ${DOCKERHUB_CREDS_USR} -p ${DOCKERHUB_CREDS_PSW}"
        echo "************************* Pushing Docker Image*************************"
        sh "docker push ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${env.DOCKER_IMAGE_TAG}"
        echo "********************* Pushed image successfull ********************"
    }
}


// This method is developed for deploying our application in different environments
def dockerDeploy(envDeploy, hostPort, contPort) {
    return {
    echo "************** Devploying to $envDeploy Environtment ******************"
    withCredentials([usernamePassword(credentialsId: 'amar_docker_creds', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
        // some block
        // With the help of this block, the slave will be connecting to docker-vm and execute the commands to containers
        //sshpass -pfoobar ssh -o StrictHostKeyChecking=no user@host command_to_run 
        //sshpass -p password -v username@ipaddress command  ip=dockervm ipaddress
        //sh "sshpass -p ${PASSWORD} -v ssh -o StrictHostKeyChecking=no ${USERNAME}@${docker_server_ip} hostname -i"
        
    script {
        // Pull the image on the Docker server
        sh "sshpass -p ${PASSWORD} -v ssh -o StrictHostKeyChecking=no ${USERNAME}@${docker_server_ip} docker pull ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"                   
        
        try {
            // Stopping the Container
            echo "************** Stoping the Container ******************"
            sh "sshpass -p ${PASSWORD} -v ssh -o StrictHostKeyChecking=no ${USERNAME}@${docker_server_ip} docker stop ${env.APPLICATION_NAME}-$envDeploy"
            
            // Remove the Container
            echo "*************** Removing the Container *****************"
            sh "sshpass -p ${PASSWORD} -v ssh -o StrictHostKeyChecking=no ${USERNAME}@${docker_server_ip} docker rm ${env.APPLICATION_NAME}-$envDeploy"
        } catch(err) {
            echo "Caught the Error: $err"
          }
        // Create a container                                                                                         HostP:ContP 
        echo "**************** Creating the Container ******************"                                                                                                                          
        sh "sshpass -p ${PASSWORD} -v ssh -o StrictHostKeyChecking=no ${USERNAME}@${docker_server_ip} docker run -d -p $hostPort:$contPort --name ${env.APPLICATION_NAME}-$envDeploy ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
    }
    }
    }
}

def imageValidation() {
    Docker docker = new Docker(this)
    K8s k8s = new K8s(this)
    return {
        println ("Pulling the docker image")
    try {
    sh "docker pull ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${env.DOCKER_IMAGE_TAG}"
    }
    catch (Exception e) {
        println("OOPS!, docker images with this tag is not available")
        println("Line Before Entering Docker Method")
        //k8s.auth_login("${env.GKE_DEV_CLUSTER_NAME}", "${env.GKE_DEV_ZONE}", "${env.GKE_DEV_PROJECT}")
        docker.buildApp("${env.APPLICATION_NAME}") 
        dockerBuildandPush().call()
    }
    }
}




// http://34.173.216.112:8080/sonarqube-webhook/


// Eureka container runs at 8761 port
// I will configure env's in a way they will have different host ports
// dev env   => 5761 (Host Port)
// test env  => 6761 (Host Port)
// stage env => 7761 (Host Port)
// dev env   => 8761 (Host Port)