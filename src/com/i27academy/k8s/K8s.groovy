package com.i27academy.k8s

class K8s {
    def jenkins
    K8s(jenkins) {
        this.jenkins = jenkins
    }
    
    def auth_login(gke_cluster_name, gke_zone, gke_project) {
        jenkins.sh"""#!/bin/bash
        echo "Entering authentication method for GKE cluster login"
        gcloud config set account jenkins@i27projects.iam.gserviceaccount.com
        # gcloud auth activate-service-account jenkins@i27projects.iam.gserviceaccount.com --key-file=key.json
        gcloud compute instances list
        echo "**************Listing number of Nodes in K8s******************"
        gcloud container clusters get-credentials $gke_cluster_name --zone $gke_zone --project $gke_project
        kubectl get nodes
        """
    }


}