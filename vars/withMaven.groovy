#!/usr/bin/env groovy

/**
 * Wraps the code in a podTemplate with the Maven container.
 * @param parameters Parameters to customize the Maven container.
 * @param body The code to wrap.
 * @return
 */
def call(Map parameters = [:], body) {

//    def defaultLabel = buildId('maven')
    def label = parameters.get('label', 'maven')
    def name = parameters.get('name', 'maven')
    def version = parameters.get('version', 'latest')
    def cloud = parameters.get('cloud', 'openshift')

    def envVars = parameters.get('envVars', [])
    def inheritFrom = parameters.get('inheritFrom', 'base')
    def namespace = parameters.get('namespace', 'openshift')
    def serviceAccount = parameters.get('serviceAccount', '')
    def workingDir = parameters.get('workingDir', '/home/jenkins')
    def mavenRepositoryClaim = parameters.get('mavenRepositoryClaim', '')
    def mavenSettingsXmlSecret = parameters.get('mavenSettingsXmlSecret', '')
    def mavenLocalRepositoryPath = parameters.get('mavenLocalRepositoryPath', "${workingDir}/.m2/respository")
    def mavenSettingsXmlMountPath = parameters.get('mavenSettingsXmlMountPath', "${workingDir}/.m2")
    def idleMinutes = parameters.get('idle', 10)

    def isPersistent = !mavenRepositoryClaim.isEmpty()
    def hasSettingsXml = !mavenSettingsXmlSecret.isEmpty()

    def internalRegistry = parameters.get('internalRegistry', findInternalRegistry(namespace: "$namespace", imagestream: "jenkins-agent-maven"))
    def mavenImage = !internalRegistry.isEmpty() ? parameters.get('mavenImage', "${internalRegistry}/${namespace}/jenkins-agent-maven:${version}") : parameters.get('mavenImage', "maven:${version}")

    def volumes = []
    envVars.add(containerEnvVar(key: 'MAVEN_OPTS', value: "-Duser.home=${workingDir} -Dmaven.repo.local=${mavenLocalRepositoryPath}"))

    if (isPersistent) {
        volumes.add(persistentVolumeClaim(claimName: "${mavenRepositoryClaim}", mountPath: "${mavenLocalRepositoryPath}"))
    } else {
        volumes.add(emptyDirVolume(mountPath: "${mavenLocalRepositoryPath}"))
    }

    if (hasSettingsXml) {
        volumes.add(secretVolume(secretName: "${mavenSettingsXmlSecret}", mountPath: "${mavenSettingsXmlMountPath}"))
    }

    podTemplate(cloud: "${cloud}", name: "${name}", namespace: "${namespace}", label: label, inheritFrom: "${inheritFrom}", serviceAccount: "${serviceAccount}",
            idleMinutesStr: "${idleMinutes}",
            containers: [containerTemplate(name: 'maven', image: "${mavenImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true, envVars: envVars)],
            volumes: volumes) {
        body()
    }
}
