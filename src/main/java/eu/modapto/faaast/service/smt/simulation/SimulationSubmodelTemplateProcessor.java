/*
 * Copyright (c) 2024 Fraunhofer IOSB, eine rechtlich nicht selbstaendige
 * Einrichtung der Fraunhofer-Gesellschaft zur Foerderung der angewandten
 * Forschung e.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.modapto.faaast.service.smt.simulation;

import de.fraunhofer.iosb.ilt.faaast.service.ServiceContext;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.AssetConnectionManager;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.lambda.provider.LambdaOperationProvider;
import de.fraunhofer.iosb.ilt.faaast.service.config.CoreConfig;
import de.fraunhofer.iosb.ilt.faaast.service.exception.ConfigurationInitializationException;
import de.fraunhofer.iosb.ilt.faaast.service.model.SemanticIdPath;
import de.fraunhofer.iosb.ilt.faaast.service.model.api.request.submodel.GetFileByPathRequest;
import de.fraunhofer.iosb.ilt.faaast.service.submodeltemplate.SubmodelTemplateProcessor;
import de.fraunhofer.iosb.ilt.faaast.service.util.DeepCopyHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.LambdaExceptionHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceBuilder;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import no.ntnu.ihb.fmi4j.importer.fmi2.CoSimulationSlave;
import no.ntnu.ihb.fmi4j.importer.fmi2.Fmu;
import org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd;
import org.eclipse.digitaltwin.aas4j.v3.model.KeyTypes;
import org.eclipse.digitaltwin.aas4j.v3.model.Operation;
import org.eclipse.digitaltwin.aas4j.v3.model.OperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementList;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultLangStringTextType;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultOperation;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultOperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultProperty;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implementation of {@link de.fraunhofer.iosb.ilt.faaast.service.filestorage.FileStorage} for file system storage.
 */
public class SimulationSubmodelTemplateProcessor implements SubmodelTemplateProcessor<SimulationSubmodelTemplateProcessorConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubmodelTemplateProcessor.class);

    private static final Reference SEMANTIC_ID_SMT_SIMULATION = ReferenceBuilder.global(
            "https://admin-shell.io/idta/SimulationModels/SimulationModels/1/0");

    private static final Reference SEMANTIC_ID_SIMULATION_MODEL = ReferenceBuilder.global(
            "https://admin-shell.io/idta/SimulationModels/SimulationModel/1/0");

    private static final Reference SEMANTIC_ID_MODEL_FILE = ReferenceBuilder.global(
            "https://admin-shell.io/idta/SimulationModels/ModelFile/1/0");

    private static final Reference SEMANTIC_ID_MODEL_FILE_VERSION = ReferenceBuilder.global(
            "https://admin-shell.io/idta/SimulationModels/ModelFileVersion/1/0");

    private static final Reference SEMANTIC_ID_DIGITAL_FILE = ReferenceBuilder.global(
            "https://admin-shell.io/idta/SimulationModels/DigitalFile/1/0");

    private static final String ARG_INSTANCE_NAME_ID = "instanceName";
    private static final String ARG_CURRENT_TIME_ID = "currentTime";
    private static final String ARG_TIME_STEP_ID = "timeStep";
    private static final String ARG_STEP_NUMBER_ID = "stepNumber";
    private static final String ARG_STEP_COUNT_ID = "stepCount";
    private static final String ARG_ARGS_PER_STEP_ID = "argumentsPerStep";
    private static final String ARG_RESULT_PER_STEP_ID = "resultPerStep";

    private static final OperationVariable ARG_INSTANCE_NAME = new DefaultOperationVariable.Builder()
            .value(new DefaultProperty.Builder()
                    .idShort(ARG_INSTANCE_NAME_ID)
                    .description(new DefaultLangStringTextType.Builder()
                            .language("en")
                            .text("name of the newly created instance")
                            .build())
                    .valueType(DataTypeDefXsd.STRING)
                    .build())
            .build();
    private static final OperationVariable ARG_CURRENT_TIME = new DefaultOperationVariable.Builder()
            .value(new DefaultProperty.Builder()
                    .idShort(ARG_CURRENT_TIME_ID)
                    .description(new DefaultLangStringTextType.Builder()
                            .language("en")
                            .text("name of the newly created instance")
                            .build())
                    .valueType(DataTypeDefXsd.STRING)
                    .build())
            .build();

    private static final OperationVariable ARG_TIME_STEP = new DefaultOperationVariable.Builder()
            .value(new DefaultProperty.Builder()
                    .idShort(ARG_TIME_STEP_ID)
                    .description(new DefaultLangStringTextType.Builder()
                            .language("en")
                            .text("time step size")
                            .build())
                    .valueType(DataTypeDefXsd.DOUBLE)
                    .build())
            .build();

    private static final OperationVariable ARG_STEP_COUNT = new DefaultOperationVariable.Builder()
            .value(new DefaultProperty.Builder()
                    .idShort(ARG_STEP_COUNT_ID)
                    .description(new DefaultLangStringTextType.Builder()
                            .language("en")
                            .text("number of steps to execute")
                            .build())
                    .valueType(DataTypeDefXsd.INTEGER)
                    .build())
            .build();

    private static final Map<Reference, Fmu> fmus = new HashMap<>();
    private static final Map<String, CoSimulationSlave> fmuInstances = new HashMap<>();
    private static final SemanticIdPath SEMANTIC_ID_PATH_TO_FMU_FILE = SemanticIdPath.builder()
            .semanticId(SEMANTIC_ID_SIMULATION_MODEL)
            .semanticId(SEMANTIC_ID_MODEL_FILE)
            .semanticId(SEMANTIC_ID_MODEL_FILE_VERSION)
            .semanticId(SEMANTIC_ID_DIGITAL_FILE)
            .build();

    private SimulationSubmodelTemplateProcessorConfig config;
    private ServiceContext serviceContext;

    @Override
    public boolean accept(Submodel submodel) {
        return Objects.nonNull(submodel) && ReferenceHelper.equals(submodel.getSemanticId(), SEMANTIC_ID_SMT_SIMULATION);
    }


    @Override
    public boolean process(Submodel submodel, AssetConnectionManager assetConnectionManager) {
        List<SubmodelElement> modelSMCs = submodel.getSubmodelElements().stream()
                .filter(Objects::nonNull)
                .filter(SubmodelElementCollection.class::isInstance)
                .toList();
        LOGGER.debug("Found {} simulation model SMCs for submodel (idShort: {}, id: {})", modelSMCs.size(), submodel.getIdShort(), submodel.getId());
        boolean modified = false;
        for (SubmodelElement modelSMC: modelSMCs) {
            try {
                Reference fmuFileRef = SEMANTIC_ID_PATH_TO_FMU_FILE.resolveUnique(submodel, KeyTypes.FILE);
                byte[] fmuBinary = serviceContext.execute(GetFileByPathRequest.builder()
                        .submodelId(ReferenceHelper.findFirstKeyType(fmuFileRef, KeyTypes.SUBMODEL))
                        .path(ReferenceHelper.toPath(fmuFileRef))
                        .build())
                        .getPayload()
                        .getContent();
                Fmu fmu = FmuHelper.loadFmu(modelSMC.getIdShort(), fmuBinary);
                fmus.put(ReferenceBuilder.forSubmodel(submodel, modelSMC), fmu);
                addCreateInstanceOperation(submodel, assetConnectionManager, modelSMC.getIdShort(), fmu);
                addDestroyInstanceOperation(submodel, assetConnectionManager, modelSMC.getIdShort(), fmu);
                addDoStepOperation(submodel, assetConnectionManager, modelSMC.getIdShort(), fmu);
                addRunSimulationOperation(submodel, assetConnectionManager, modelSMC.getIdShort(), fmu);
                modified = true;
            }
            catch (IOException e) {
                LOGGER.warn("Error loading FMU model (idShort: {}, id: {})", submodel.getIdShort(), submodel.getId(), e);
            }
        }
        return modified;
    }


    private void addCreateInstanceOperation(Submodel submodel, AssetConnectionManager assetConnectionManager, String modelName, Fmu fmu) throws IOException {
        Operation operation = new DefaultOperation.Builder()
                .idShort(String.format("%s-CreateInstance", modelName))
                .inputVariables(FmuHelper.getInputArgumentsMetadata(fmu))
                .outputVariables(ARG_INSTANCE_NAME)
                .build();
        submodel.getSubmodelElements().add(operation);
        assetConnectionManager.registerLambdaOperationProvider(
                ReferenceBuilder.forSubmodel(submodel, operation),
                LambdaOperationProvider.builder()
                        .handle(LambdaExceptionHelper.rethrowBiFunction((OperationVariable[] input, OperationVariable[] inoutput) -> {
                            return handleCreateInstanceOperation(modelName, fmu, input, inoutput);
                        }))
                        .build());
    }


    private OperationVariable[] handleCreateInstanceOperation(String modelName, Fmu fmu, OperationVariable[] input, OperationVariable[] inoutput) throws IOException {
        String instanceName = String.format("%s-%s", modelName, UUID.randomUUID());
        CoSimulationSlave fmuInstance = FmuHelper.createInstance(instanceName, fmu, Arrays.asList(input));
        fmuInstances.put(instanceName, fmuInstance);
        return new OperationVariable[] {
                newInstanceNameArg(instanceName)
        };
    }


    private void addDestroyInstanceOperation(Submodel submodel, AssetConnectionManager assetConnectionManager, String modelName, Fmu fmu) throws IOException {
        Operation operation = new DefaultOperation.Builder()
                .idShort(String.format("%s-DestroyInstance", modelName))
                .inputVariables(List.of(ARG_INSTANCE_NAME))
                .outputVariables(List.of())
                .build();
        submodel.getSubmodelElements().add(operation);
        assetConnectionManager.registerLambdaOperationProvider(
                ReferenceBuilder.forSubmodel(submodel, operation),
                LambdaOperationProvider.builder()
                        .handle(LambdaExceptionHelper.rethrowBiFunction(this::handleDestroyInstanceOperation))
                        .build());
    }


    private OperationVariable[] handleDestroyInstanceOperation(OperationVariable[] input, OperationVariable[] inoutput) throws IOException {
        String instanceName = requireArgument(input, ARG_INSTANCE_NAME_ID, DataTypeDefXsd.STRING);
        if (!fmuInstances.containsKey(instanceName)) {
            throw new IllegalArgumentException(String.format("instance does not exist (name: %s)", instanceName));
        }
        CoSimulationSlave fmuInstance = fmuInstances.get(instanceName);
        fmuInstance.close();
        fmuInstances.remove(instanceName);
        return new OperationVariable[] {};
    }


    private void addDoStepOperation(Submodel submodel, AssetConnectionManager assetConnectionManager, String modelName, Fmu fmu) throws IOException {
        List<OperationVariable> inputVariables = FmuHelper.getInputArgumentsMetadata(fmu);
        inputVariables.add(0, ARG_INSTANCE_NAME);
        inputVariables.add(1, ARG_CURRENT_TIME);
        inputVariables.add(2, ARG_TIME_STEP);
        Operation operation = new DefaultOperation.Builder()
                .idShort(String.format("%s-DoStep", modelName))
                .inputVariables(inputVariables)
                .outputVariables(FmuHelper.getOutputArgumentsMetadata(fmu))
                .build();
        submodel.getSubmodelElements().add(operation);
        assetConnectionManager.registerLambdaOperationProvider(
                ReferenceBuilder.forSubmodel(submodel, operation),
                LambdaOperationProvider.builder()
                        .handle(LambdaExceptionHelper.rethrowBiFunction(this::handleDoStepOperation))
                        .build());
    }


    private OperationVariable[] handleDoStepOperation(OperationVariable[] input, OperationVariable[] inoutput) throws IOException {
        String instanceName = requireArgument(input, ARG_INSTANCE_NAME_ID, DataTypeDefXsd.STRING);
        if (!fmuInstances.containsKey(instanceName)) {
            throw new IllegalArgumentException(String.format("instance does not exist (name: %s)", instanceName));
        }
        CoSimulationSlave fmuInstance = fmuInstances.get(instanceName);
        double t = Double.parseDouble(requireArgument(input, ARG_CURRENT_TIME_ID, DataTypeDefXsd.DOUBLE));
        double dt = Double.parseDouble(requireArgument(input, ARG_TIME_STEP_ID, DataTypeDefXsd.DOUBLE));
        FmuHelper.setFmuInputVariablesFromAas(fmuInstance, Arrays.asList(input));
        fmuInstance.doStep(t, dt);
        if (!fmuInstance.getLastStatus().isOK()) {
            throw new RuntimeException(String.format("executing FMU step failed"));
        }
        return FmuHelper.getOutputArgumentsWithValues(fmuInstance).toArray(OperationVariable[]::new);
    }


    private void addRunSimulationOperation(Submodel submodel, AssetConnectionManager assetConnectionManager, String modelName, Fmu fmu) throws IOException {
        List<OperationVariable> inputVariables = List.of(
                ARG_CURRENT_TIME,
                ARG_TIME_STEP,
                ARG_STEP_COUNT,
                newMultiStepArg(FmuHelper.getInputArgumentsMetadata(fmu)));
        List<OperationVariable> outputVariables = List.of(newMultiStepResult(FmuHelper.getOutputArgumentsMetadata(fmu)));

        Operation operation = new DefaultOperation.Builder()
                .idShort(String.format("%s-RunSimulation", modelName))
                .inputVariables(inputVariables)
                .outputVariables(outputVariables)
                .build();
        submodel.getSubmodelElements().add(operation);
        assetConnectionManager.registerLambdaOperationProvider(
                ReferenceBuilder.forSubmodel(submodel, operation),
                LambdaOperationProvider.builder()
                        .handle(LambdaExceptionHelper.rethrowBiFunction((OperationVariable[] input, OperationVariable[] inoutput) -> {
                            return handleRunSimulationOperation(fmu, input, inoutput);
                        }))
                        .build());
    }


    private OperationVariable[] handleRunSimulationOperation(Fmu fmu, OperationVariable[] input, OperationVariable[] inoutput) throws IOException {
        int stepCount = Integer.parseInt(requireArgument(input, ARG_STEP_COUNT_ID, DataTypeDefXsd.INTEGER));
        double t = Double.parseDouble(optionalArgument(inoutput, ARG_CURRENT_TIME_ID, DataTypeDefXsd.DOUBLE).orElse("0"));
        double dt = Double.parseDouble(requireArgument(input, ARG_TIME_STEP_ID, DataTypeDefXsd.DOUBLE));
        Map<Integer, List<OperationVariable>> multiStepInput = parseMultiStepInput(input);
        CoSimulationSlave fmuInstance = FmuHelper.createInstance(UUID.randomUUID().toString(), fmu, List.of());
        SubmodelElementList resultList = new DefaultSubmodelElementList.Builder()
                .idShort(ARG_RESULT_PER_STEP_ID)
                .build();
        for (int i = 1; i <= stepCount; i++) {
            List<OperationVariable> inputForStep = multiStepInput.getOrDefault(i, List.of());
            FmuHelper.setFmuInputVariablesFromAas(fmuInstance, inputForStep);
            fmuInstance.doStep(t, dt);
            if (!fmuInstance.getLastStatus().isOK()) {
                throw new RuntimeException(String.format("executing FMU step failed"));
            }
            resultList.getValue().add(new DefaultSubmodelElementCollection.Builder()
                    .value(Stream.concat(
                            Stream.of(new DefaultProperty.Builder()
                                    .idShort(ARG_STEP_NUMBER_ID)
                                    .valueType(DataTypeDefXsd.INTEGER)
                                    .value(Integer.toString(i))
                                    .build()),
                            FmuHelper.getOutputArgumentsWithValues(fmuInstance).stream().map(OperationVariable::getValue))
                            .toList())
                    .build());
            t += dt;
        }
        return new OperationVariable[] {
                new DefaultOperationVariable.Builder()
                        .value(resultList)
                        .build()
        };
    }


    private static Map<Integer, List<OperationVariable>> parseMultiStepInput(OperationVariable[] input) {
        Map<Integer, List<OperationVariable>> result = new HashMap<>();
        if (Objects.isNull(input)) {
            return result;
        }
        for (OperationVariable argument: input) {
            if (Objects.isNull(argument)
                    || Objects.isNull(argument.getValue())
                    || !Objects.equals(ARG_ARGS_PER_STEP_ID, argument.getValue().getIdShort())
                    || !SubmodelElementList.class.isInstance(argument.getValue())) {
                continue;
            }
            SubmodelElementList argumentsList = (SubmodelElementList) argument.getValue();
            for (SubmodelElement argumentsPerStep: argumentsList.getValue()) {
                if (Objects.isNull(argumentsPerStep)
                        || !SubmodelElementCollection.class.isInstance(argumentsPerStep)
                        || StringHelper.isEmpty(argumentsPerStep.getIdShort())) {
                    continue;
                }
                SubmodelElementCollection argumentsCollection = (SubmodelElementCollection) argumentsPerStep;
                Optional<Property> stepNumber = argumentsCollection.getValue().stream()
                        .filter(Objects::nonNull)
                        .filter(x -> Objects.equals(ARG_STEP_NUMBER_ID, x.getIdShort()))
                        .filter(Property.class::isInstance)
                        .map(Property.class::cast)
                        .filter(x -> Objects.equals(DataTypeDefXsd.INTEGER, x.getValue()))
                        .findFirst();
                if (!stepNumber.isPresent()) {
                    throw new IllegalArgumentException(String.format("SubmodelElementCollection missing argument %s", ARG_STEP_NUMBER_ID));
                }
                result.put(
                        Integer.parseInt(stepNumber.get().getValue()),
                        ((SubmodelElementCollection) argumentsPerStep).getValue().stream()
                                .filter(Objects::nonNull)
                                .filter(Property.class::isInstance)
                                .filter(x -> !Objects.equals(ARG_STEP_NUMBER_ID, x.getIdShort()))
                                .map(x -> new DefaultOperationVariable.Builder()
                                        .value(x)
                                        .build())
                                .map(OperationVariable.class::cast)
                                .toList());

            }
        }
        return result;
    }


    private static String requireArgument(OperationVariable[] arguments, String name, DataTypeDefXsd datatype) {
        return Stream.of(arguments)
                .map(OperationVariable::getValue)
                .filter(x -> Objects.equals(name, x.getIdShort()))
                .filter(Property.class::isInstance)
                .map(Property.class::cast)
                .filter(x -> Objects.equals(datatype, x.getValueType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("missing required argument (name: %s, datatype: %s)", name, datatype)))
                .getValue();
    }


    private static Optional<String> optionalArgument(OperationVariable[] arguments, String name, DataTypeDefXsd datatype) {
        return Stream.of(arguments)
                .map(OperationVariable::getValue)
                .filter(x -> Objects.equals(name, x.getIdShort()))
                .filter(Property.class::isInstance)
                .map(Property.class::cast)
                .filter(x -> Objects.equals(datatype, x.getValueType()))
                .findFirst()
                .map(x -> x.getValue());
    }


    private static OperationVariable newMultiStepArg(List<OperationVariable> originalArgs) {
        return new DefaultOperationVariable.Builder()
                .value(new DefaultSubmodelElementList.Builder()
                        .idShort(ARG_ARGS_PER_STEP_ID)
                        .value(new DefaultSubmodelElementCollection.Builder()
                                .value(
                                        Stream.concat(
                                                Stream.of(new DefaultProperty.Builder()
                                                        .idShort(ARG_STEP_NUMBER_ID)
                                                        .valueType(DataTypeDefXsd.INTEGER)
                                                        .build()),
                                                originalArgs.stream().map(OperationVariable::getValue))
                                                .toList())
                                .build())
                        .build())
                .build();
    }


    private static OperationVariable newMultiStepResult(List<OperationVariable> originalArgs) {
        return new DefaultOperationVariable.Builder()
                .value(new DefaultSubmodelElementList.Builder()
                        .idShort(ARG_RESULT_PER_STEP_ID)
                        .value(new DefaultSubmodelElementCollection.Builder()
                                .value(
                                        Stream.concat(
                                                Stream.of(new DefaultProperty.Builder()
                                                        .idShort(ARG_STEP_NUMBER_ID)
                                                        .valueType(DataTypeDefXsd.INTEGER)
                                                        .build()),
                                                originalArgs.stream().map(OperationVariable::getValue))
                                                .toList())
                                .build())
                        .build())
                .build();
    }


    private static OperationVariable newInstanceNameArg(String instanceName) {
        Property property = DeepCopyHelper.deepCopy(ARG_INSTANCE_NAME.getValue(), Property.class);
        property.setValue(instanceName);
        return new DefaultOperationVariable.Builder()
                .value(property)
                .build();
    }


    @Override
    public void init(CoreConfig coreConfig, SimulationSubmodelTemplateProcessorConfig config, ServiceContext serviceContext) throws ConfigurationInitializationException {
        this.config = config;
        this.serviceContext = serviceContext;
    }


    @Override
    public SimulationSubmodelTemplateProcessorConfig asConfig() {
        return config;
    }

}
