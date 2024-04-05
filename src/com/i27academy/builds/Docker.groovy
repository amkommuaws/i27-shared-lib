package com.i27academy.builds

class Docker {
    def jenkins
    Docker(jenkins) {
        this.jenkins = jenkins
    }


    //Application Build method
    def buildApp(appName) {
        jenkins.sh"""#!/bin/bash
        echo "Building the shared Library $appName Application"
        mvn clean package -DskipTests=true
        """
    }
}