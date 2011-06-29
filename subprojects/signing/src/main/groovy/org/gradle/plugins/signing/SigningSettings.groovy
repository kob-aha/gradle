/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.signing

import org.gradle.api.Task
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.IConventionAware

import org.gradle.plugins.signing.signatory.*

import org.gradle.plugins.signing.type.SignatureType
import org.gradle.plugins.signing.type.SignatureTypeProvider
import org.gradle.plugins.signing.type.DefaultSignatureTypeProvider

import org.gradle.plugins.signing.signatory.pgp.PgpSignatoryProvider

class SigningSettings {
    
    static final String DEFAULT_CONFIGURATION_NAME = "signatures"
    
    private Project project
    private Configuration configuration
    private SignatureTypeProvider typeProvider
    private SignatoryProvider signatories
    private boolean required = true
    
    SigningSettings(Project project) {
        this.project = project
        this.configuration = getDefaultConfiguration()
        this.typeProvider = createSignatureTypeProvider()
        this.signatories = createSignatoryProvider()
    }
    
    protected Configuration getDefaultConfiguration() {
        def configurations = project.configurations
        def configuration = configurations.findByName(DEFAULT_CONFIGURATION_NAME)
        if (configuration == null) {
            configuration = configurations.add(DEFAULT_CONFIGURATION_NAME)
        }
        configuration
    }
    
    protected SignatureTypeProvider createSignatureTypeProvider() {
        new DefaultSignatureTypeProvider()
    }
    
    protected SignatoryProvider createSignatoryProvider() {
        new PgpSignatoryProvider()
    }
    
    SignatoryProvider signatories(Closure block) {
        signatories.configure(this, block)
        signatories
    }
        
    Signatory getSignatory() {
        signatories.getDefaultSignatory(project)
    }
    
    SignatureType getSignatureType() {
        typeProvider.defaultType
    }
    
    Configuration getConfiguration() {
        configuration
    }
    
    boolean getRequired() {
        required
    }
    
    void setRequired(boolean required) {
        this.required = required
    }
    
    void required(boolean required) {
        setRequired(required)
    }
    
    void addSignatureSpecConventions(SignatureSpec spec) {
        if (!(spec instanceof IConventionAware)) {
            throw new InvalidUserDataException("Cannot add conventions to signature spec '$spec' as it is not convention aware")
        }
        
        spec.conventionMapping.map('signatory') { getSignatory() }
        spec.conventionMapping.map('signatureType') { getSignatureType() }
    }
    
    Sign sign(Task task) {
        sign([task] as Task[]).first()
    }
    
    Collection<Sign> sign(Task[] tasksToSign) {
        tasksToSign.collect { taskToSign ->
            def signTask = project.task("sign${taskToSign.name.capitalize()}", type: Sign) {
                sign taskToSign
            }
            configuration.addArtifact(signTask.singleSignature)
            signTask
        }
    }
    
    Sign sign(PublishArtifact artifact) {
        def signTask = project.task("sign${artifact.name.capitalize()}", type: Sign) {
            delegate.sign artifact
        }
        configuration.addArtifact(signTask.singleSignature)
        signTask
    }
    
    Sign sign(Configuration configuration) {
        sign([configuration] as Configuration[]).first()
    }
    
    Collection<Sign> sign(Configuration[] configurations) {
        configurations.collect { configuration ->
            def signTask = project.task("sign${configuration.name.capitalize()}", type: Sign) {
                sign configuration
            }
            signTask.signatures.each { getConfiguration().addArtifact(it) }
            signTask
        }
    }
}