/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.history.changes;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.Describable;
import org.gradle.internal.change.CachingChangeContainer;
import org.gradle.internal.change.Change;
import org.gradle.internal.change.ChangeContainer;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.change.ErrorHandlingChangeContainer;
import org.gradle.internal.change.SummarizingChangeContainer;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;

public class DefaultExecutionStateChangeDetector implements ExecutionStateChangeDetector {
    @Override
    public ExecutionStateChanges detectChanges(AfterPreviousExecutionState lastExecution, BeforeExecutionState thisExecution, Describable executable, boolean allowOverlappingOutputs) {
        // Capture changes in execution outcome
        ChangeContainer previousSuccessState = new PreviousSuccessChanges(
            lastExecution.isSuccessful());

        // Capture changes to implementation
        ChangeContainer implementationChanges = new ImplementationChanges(
            lastExecution.getImplementation(), lastExecution.getAdditionalImplementations(),
            thisExecution.getImplementation(), thisExecution.getAdditionalImplementations(),
            executable);

        // Capture non-file input changes
        ChangeContainer inputPropertyChanges = new PropertyChanges(
            lastExecution.getInputProperties(),
            thisExecution.getInputProperties(),
            "Input",
            executable);
        ChangeContainer inputPropertyValueChanges = new InputValueChanges(
            lastExecution.getInputProperties(),
            thisExecution.getInputProperties(),
            executable);

        // Capture input files state
        ChangeContainer inputFilePropertyChanges = new PropertyChanges(
            lastExecution.getInputFileProperties(),
            thisExecution.getInputFileProperties(),
            "Input file",
            executable);
        InputFileChanges directInputFileChanges = new DefaultInputFileChanges(
            lastExecution.getInputFileProperties(),
            thisExecution.getInputFileProperties());
        InputFileChanges inputFileChanges = errorHandling(executable, caching(directInputFileChanges));

        // Capture output files state
        ChangeContainer outputFilePropertyChanges = new PropertyChanges(
            lastExecution.getOutputFileProperties(),
            thisExecution.getOutputFileProperties(),
            "Output",
            executable);
        OutputFileChanges uncachedOutputChanges = new OutputFileChanges(
            lastExecution.getOutputFileProperties(),
            thisExecution.getOutputFileProperties(),
            allowOverlappingOutputs);
        ChangeContainer outputFileChanges = caching(uncachedOutputChanges);

        ChangeContainer rebuildTriggeringChanges = errorHandling(executable, new SummarizingChangeContainer(previousSuccessState, implementationChanges, inputPropertyChanges, inputPropertyValueChanges, outputFilePropertyChanges, outputFileChanges, inputFilePropertyChanges));

        ImmutableList.Builder<String> builder = ImmutableList.builder();
        MessageCollectingChangeVisitor visitor = new MessageCollectingChangeVisitor(builder, ExecutionStateChangeDetector.MAX_OUT_OF_DATE_MESSAGES);
        rebuildTriggeringChanges.accept(visitor);
        ImmutableList<String> rebuildReasons = builder.build();

        boolean rebuildRequired = !rebuildReasons.isEmpty();

        if (!rebuildRequired) {
            inputFileChanges.accept(visitor);
        }

        ImmutableList<String> allChangeMessages = builder.build();
        return rebuildRequired
            ? new NonIncrementalDetectedExecutionStateChanges(allChangeMessages, thisExecution.getInputFileProperties())
            : new IncrementalDetectedExecutionStateChanges(inputFileChanges, allChangeMessages);
    }

    private static ChangeContainer caching(ChangeContainer wrapped) {
        return new CachingChangeContainer(MAX_OUT_OF_DATE_MESSAGES, wrapped);
    }

    private static InputFileChanges caching(InputFileChanges wrapped) {
        CachingChangeContainer cachingChangeContainer = new CachingChangeContainer(MAX_OUT_OF_DATE_MESSAGES, wrapped);
        return new InputFileChangesWrapper(wrapped, cachingChangeContainer);
    }

    private static ChangeContainer errorHandling(Describable executable, ChangeContainer wrapped) {
        return new ErrorHandlingChangeContainer(executable, wrapped);
    }

    private static InputFileChanges errorHandling(Describable executable, InputFileChanges wrapped) {
        ErrorHandlingChangeContainer errorHandlingChangeContainer = new ErrorHandlingChangeContainer(executable, wrapped);
        return new InputFileChangesWrapper(wrapped, errorHandlingChangeContainer);
    }

    private static class InputFileChangesWrapper implements InputFileChanges {
        private final InputFileChanges inputFileChangesDelegate;
        private final ChangeContainer changeContainerDelegate;

        public InputFileChangesWrapper(InputFileChanges inputFileChangesDelegate, ChangeContainer changeContainerDelegate) {
            this.inputFileChangesDelegate = inputFileChangesDelegate;
            this.changeContainerDelegate = changeContainerDelegate;
        }

        @Override
        public boolean accept(String propertyName, ChangeVisitor visitor) {
            return inputFileChangesDelegate.accept(propertyName, visitor);
        }

        @Override
        public boolean accept(ChangeVisitor visitor) {
            return changeContainerDelegate.accept(visitor);
        }
    }

    private static class IncrementalDetectedExecutionStateChanges extends AbstractDetectedExecutionStateChanges implements ExecutionStateChanges {
        private final InputFileChanges inputFileChanges;

        public IncrementalDetectedExecutionStateChanges(
            InputFileChanges inputFileChanges,
            ImmutableList<String> allChangeMessages
        ) {
            super(allChangeMessages);
            this.inputFileChanges = inputFileChanges;
        }

        @Override
        public InputChangesInternal createInputChanges(ImmutableMultimap<Object, String> incrementalParameterNameByValue) {
            return new IncrementalInputChanges(inputFileChanges, incrementalParameterNameByValue);
        }
    }

    private static class NonIncrementalDetectedExecutionStateChanges extends AbstractDetectedExecutionStateChanges implements ExecutionStateChanges {
        private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties;

        public NonIncrementalDetectedExecutionStateChanges(
            ImmutableList<String> allChangeMessages,
            ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties
        ) {
            super(allChangeMessages);
            this.inputFileProperties = inputFileProperties;
        }

        @Override
        public InputChangesInternal createInputChanges(ImmutableMultimap<Object, String> incrementalParameterNameByValue) {
            return new NonIncrementalInputChanges(inputFileProperties, incrementalParameterNameByValue);
        }
    }

    private static class AbstractDetectedExecutionStateChanges {
        private final ImmutableList<String> allChangeMessages;

        public AbstractDetectedExecutionStateChanges(ImmutableList<String> allChangeMessages) {
            this.allChangeMessages = allChangeMessages;
        }

        public ImmutableList<String> getAllChangeMessages() {
            return allChangeMessages;
        }
    }

    private static class MessageCollectingChangeVisitor implements ChangeVisitor {
        private final ImmutableCollection.Builder<String> messages;
        private final int max;
        private int count;

        public MessageCollectingChangeVisitor(ImmutableCollection.Builder<String> messages, int max) {
            this.messages = messages;
            this.max = max;
        }

        @Override
        public boolean visitChange(Change change) {
            messages.add(change.getMessage());
            return ++count < max;
        }
    }
}
