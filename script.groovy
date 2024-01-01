def cleanup() {
        cleanWs()
}

def checkout() {
        git branch: 'main', credentialsId: 'github', url: "$GITHUB_URL"
}

def owasp() {
    dependencyCheck additionalArguments: '--scan ./ --disableYarnAudit --disableNodeAudit', odcInstallation: 'DP-check'
    dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
}

def sonaranalysis() {
        withSonarQubeEnv(installationName: 'SonarQube', credentialsId: 'sonar') {
            sh "mvn sonar:sonar"
    }
}

def qualitygate() {
        waitForQualityGate abortPipeline: false, credentialsId: 'sonar'
}

def trivyfs() {
        sh "trivy fs ."
}

def codecompile() {
        sh '''
                mvn  clean compile
        '''
}

def buildapplication() {
    sh '''
                mvn clean install
        '''
}

def dockerbuild() {
        sh '''
                docker build . -t ${IMAGE_NAME}:${BUILD_NUMBER}
                
        '''
 
}

def trivyimage() {
        sh '''
                trivy image ${IMAGE_NAME}:${BUILD_NUMBER}
        '''
        
}

def grype() {
        sh '''
                grype ${IMAGE_NAME}:${BUILD_NUMBER}
        '''       
}

def syft() {
        sh '''
                syft ${IMAGE_NAME}:${BUILD_NUMBER}

        ''' 
}

def dockerrun() {
    sh '''
        docker run -dt -p 8010:8080 ${IMAGE_NAME}:${BUILD_NUMBER}
        docker ps -aq | xargs docker stop
        '''
}

def dockernexus() {
        withCredentials([usernamePassword(credentialsId: 'nexus', passwordVariable: 'nexusPassword', usernameVariable: 'nexusUser')]) {
                sh "docker image tag ${IMAGE_NAME}:${BUILD_NUMBER} ${NEXUS_IMAGE_URL}/${IMAGE_NAME}:${BUILD_NUMBER}"
                sh "docker login -u ${env.nexusUser} -p ${env.nexusPassword} ${NEXUS_IMAGE_URL}"
                sh "docker push ${NEXUS_IMAGE_URL}/${IMAGE_NAME}:${BUILD_NUMBER}"
                sh "docker rmi ${NEXUS_IMAGE_URL}/${IMAGE_NAME}:${BUILD_NUMBER}"
        }
}

def dockerscout() {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', passwordVariable: 'dockerHubPassword', usernameVariable: 'dockerHubUser')]) {
                sh "docker login -u ${env.dockerHubUser} -p ${env.dockerHubPassword} "
                sh "docker scout quickview ${IMAGE_NAME}:${BUILD_NUMBER}"
                sh "docker scout cves ${IMAGE_NAME}:${BUILD_NUMBER}"
                sh "docker scout recommendations ${IMAGE_NAME}:${BUILD_NUMBER}"
        }
}

def manifast() {        
        sh '''
                sed -i 's+10.0.1.3:8082/image.*+10.0.1.3:8082/image:${BUILD_NUMBER}+g' kubernetes/deployment.yaml
        '''
}


def removedocker() {
                sh "docker system prune --force --all"
                sh "docker system prune --force --all --volumes"
}


return this
