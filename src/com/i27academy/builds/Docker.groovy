package com.i27academy.builds

class Docker {
    def jenkins
    Docker(jenkins) {
        this.jenkins = jenkins
    }
    //Addition Method
    def add(firstNumber, secondNumber) {
        //logic
        return firstNumber+secondNumber 
    }


    //Application Build method
    def buildApp() {
        jenkins.sh"""#!/bin/bash
        echo "Building the shared Library Eureka Application"
        mvn clean package -DskipTests=true
        """
    }
}