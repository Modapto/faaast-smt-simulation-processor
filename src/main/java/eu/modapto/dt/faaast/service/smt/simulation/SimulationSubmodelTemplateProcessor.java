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
package eu.modapto.dt.faaast.service.smt.simulation;

import static eu.modapto.dt.faaast.service.smt.simulation.Constants.ARG_ARGS_PER_STEP_ID;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.ARG_CURRENT_TIME;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.ARG_CURRENT_TIME_ID;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.ARG_RESULT_PER_STEP_ID;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.ARG_STEP_COUNT;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.ARG_STEP_COUNT_ID;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.ARG_STEP_NUMBER_ID;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.ARG_TIME_STEP;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.ARG_TIME_STEP_ID;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.SEMANTIC_ID_DIGITAL_FILE;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.SEMANTIC_ID_MODEL_FILE;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.SEMANTIC_ID_MODEL_FILE_VERSION;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.SEMANTIC_ID_PARAM_FILE;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.SEMANTIC_ID_SIMULATION_MODEL;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.SEMANTIC_ID_SMT_SIMULATION;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.SMC_SIMULATION_MODELS_PREFIX;

import de.fraunhofer.iosb.ilt.faaast.service.ServiceContext;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.AssetConnectionManager;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.lambda.provider.LambdaOperationProvider;
import de.fraunhofer.iosb.ilt.faaast.service.config.CoreConfig;
import de.fraunhofer.iosb.ilt.faaast.service.exception.ConfigurationInitializationException;
import de.fraunhofer.iosb.ilt.faaast.service.model.IdShortPath;
import de.fraunhofer.iosb.ilt.faaast.service.model.SemanticIdPath;
import de.fraunhofer.iosb.ilt.faaast.service.model.api.request.submodel.GetFileByPathRequest;
import de.fraunhofer.iosb.ilt.faaast.service.model.api.response.submodel.GetFileByPathResponse;
import de.fraunhofer.iosb.ilt.faaast.service.model.exception.AmbiguousElementException;
import de.fraunhofer.iosb.ilt.faaast.service.model.exception.ResourceNotFoundException;
import de.fraunhofer.iosb.ilt.faaast.service.model.submodeltemplate.Cardinality;
import de.fraunhofer.iosb.ilt.faaast.service.submodeltemplate.SubmodelTemplateProcessor;
import de.fraunhofer.iosb.ilt.faaast.service.util.EnvironmentHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.LambdaExceptionHelper;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceBuilder;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceHelper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.ntnu.ihb.fmi4j.importer.fmi2.CoSimulationSlave;
import no.ntnu.ihb.fmi4j.importer.fmi2.Fmu;
import org.eclipse.digitaltwin.aas4j.v3.model.AasSubmodelElements;
import org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd;
import org.eclipse.digitaltwin.aas4j.v3.model.File;
import org.eclipse.digitaltwin.aas4j.v3.model.KeyTypes;
import org.eclipse.digitaltwin.aas4j.v3.model.Operation;
import org.eclipse.digitaltwin.aas4j.v3.model.OperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.QualifierKind;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementList;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultExtension;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultOperation;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultOperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultProperty;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultQualifier;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implementation of {@link de.fraunhofer.iosb.ilt.faaast.service.filestorage.FileStorage} for file system storage.
 */
public class SimulationSubmodelTemplateProcessor implements SubmodelTemplateProcessor<SimulationSubmodelTemplateProcessorConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubmodelTemplateProcessor.class);

    private static final Map<Reference, Fmu> fmus = new HashMap<>();
    private static final Map<String, CoSimulationSlave> fmuInstances = new HashMap<>();

    private SimulationSubmodelTemplateProcessorConfig config;
    private ServiceContext serviceContext;

    @Override
    public boolean accept(Submodel submodel) {
        return Objects.nonNull(submodel) && ReferenceHelper.equals(submodel.getSemanticId(), SEMANTIC_ID_SMT_SIMULATION);
    }


    private String getModelName(SubmodelElementCollection modelSMC) {
        String result = modelSMC.getIdShort();
        if (result.toLowerCase().startsWith(SMC_SIMULATION_MODELS_PREFIX.toLowerCase())) {
            result = result.substring(SMC_SIMULATION_MODELS_PREFIX.length());
        }
        return result;
    }


    private static Reference getFmuFileReference(Submodel submodel, SubmodelElementCollection smcSimulationModel) throws ResourceNotFoundException {
        return ReferenceHelper.combine(
                ReferenceBuilder.forSubmodel(submodel),
                SemanticIdPath.builder()
                        .semanticId(SEMANTIC_ID_MODEL_FILE)
                        .semanticId(SEMANTIC_ID_MODEL_FILE_VERSION)
                        .semanticId(SEMANTIC_ID_DIGITAL_FILE)
                        .build()
                        .resolveUnique(smcSimulationModel, KeyTypes.FILE));
    }


    private byte[] getFmuFile(Submodel submodel, SubmodelElementCollection smcSimulationModel) {
        try {
            Reference fmuFileRef = getFmuFileReference(submodel, smcSimulationModel);
            GetFileByPathResponse response = serviceContext.execute(GetFileByPathRequest.builder()
                    .internal()
                    .submodelId(submodel.getId())
                    .path(ReferenceHelper.toPath(fmuFileRef))
                    .build());
            if (!response.getStatusCode().isSuccess() || Objects.isNull(response.getPayload())) {
                if (Objects.nonNull(response.getResult()) && Objects.nonNull(response.getResult().getMessages())) {
                    LOGGER.warn("Reason: "
                            + System.lineSeparator()
                            + response.getResult().getMessages().stream()
                                    .map(x -> String.format("   [%s] %s (code: %s)", x.getMessageType(), x.getText(), x.getCode()))
                                    .collect(Collectors.joining(System.lineSeparator())));
                    throw new FmuException(String.format("Failed to load FMU for SMT Simulation (submodelId: %s)", submodel.getId()));
                }

            }
            return response.getPayload().getContent();
        }
        catch (Exception e) {
            throw new FmuException(String.format("Failed to load FMU for SMT Simulation (submodelId: %s)", submodel.getId()), e);
        }
    }


    @Override
    public boolean process(Submodel submodel, AssetConnectionManager assetConnectionManager) {
        List<SubmodelElementCollection> smcSimulationModels = SemanticIdPath.builder()
                .semanticId(SEMANTIC_ID_SIMULATION_MODEL)
                .build()
                .resolve(submodel, SubmodelElementCollection.class);
        LOGGER.debug("Found {} simulation model SMCs for submodel (idShort: {}, id: {})", smcSimulationModels.size(), submodel.getIdShort(), submodel.getId());
        boolean modified = false;
        for (SubmodelElementCollection smcSimulationModel: smcSimulationModels) {
            try {
                String name = getModelName(smcSimulationModel);
                byte[] fmuBinary = getFmuFile(submodel, smcSimulationModel);
                Fmu fmu = FmuHelper.loadFmu(name, fmuBinary);
                fmus.put(ReferenceBuilder.forSubmodel(submodel, smcSimulationModel), fmu);
                addRunSimulationOperation(
                        submodel,
                        assetConnectionManager,
                        name,
                        getFmuFileReference(submodel, smcSimulationModel),
                        fmu,
                        getInitialParameters(submodel, smcSimulationModel));
                modified = true;
            }
            catch (Exception e) {
                LOGGER.warn("Error loading FMU model (idShort: {}, id: {})", submodel.getIdShort(), submodel.getId(), e);
            }
        }
        return modified;
    }


    private Map<String, String> getInitialParameters(Submodel submodel, SubmodelElementCollection smcSimulation) {
        Map<String, String> result = new HashMap<>();
        try {
            Optional<File> paramFile = SemanticIdPath.builder()
                    .semanticId(SEMANTIC_ID_PARAM_FILE)
                    .build()
                    .resolveOptional(smcSimulation, File.class);
            if (paramFile.isEmpty()) {
                return result;
            }
            byte[] paramFileContent = serviceContext.execute(
                    GetFileByPathRequest.builder()
                            .internal()
                            .submodelId(submodel.getId())
                            .path(IdShortPath.builder()
                                    .idShort(smcSimulation.getIdShort())
                                    .idShort(paramFile.get().getIdShort())
                                    .build().toString())
                            .build())
                    .getPayload()
                    .getContent();

            Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(paramFileContent));
            result = properties.stringPropertyNames().stream().collect(Collectors.toMap(x -> x, x -> properties.getProperty(x)));
        }
        catch (IOException e) {
            LOGGER.warn("Failed to to initial parameters for SMT Simulation (submodelId: {})", submodel.getId(), e);
        }
        return result;
    }


    private static Operation findExistingOperationForFmu(Submodel submodel, Reference fmuReference) {
        return submodel.getSubmodelElements().stream()
                .filter(Operation.class::isInstance)
                .map(Operation.class::cast)
                .filter(x -> Objects.nonNull(x.getExtensions()))
                .filter(x -> x.getExtensions().stream().anyMatch(e -> Objects.equals(Constants.EXTENSION_KEY_OPERATION_TO_DIGITAL_FILE_LINK, e.getName())
                        && Objects.nonNull(ReferenceHelper.findSameReference(e.getRefersTo(), fmuReference))))
                .findFirst()
                .orElse(null);
    }


    private void addRunSimulationOperation(Submodel submodel,
                                           AssetConnectionManager assetConnectionManager,
                                           String modelName,
                                           Reference fmuReference,
                                           Fmu fmu,
                                           Map<String, String> initalParameters)
            throws IOException {
        Operation operation = findExistingOperationForFmu(submodel, fmuReference);
        if (Objects.isNull(operation)) {
            LOGGER.debug("creating new operation for FMU (FMU reference: {})", ReferenceHelper.asString(fmuReference));
            operation = new DefaultOperation.Builder()
                    .idShort(modelName)
                    .extensions(new DefaultExtension.Builder()
                            .name(Constants.EXTENSION_KEY_OPERATION_TO_DIGITAL_FILE_LINK)
                            .refersTo(fmuReference)
                            .build())
                    .inputVariables(List.of(
                            ARG_CURRENT_TIME,
                            ARG_TIME_STEP,
                            ARG_STEP_COUNT,
                            newMultiStepArg(FmuHelper.getInputArgumentsMetadata(fmu))))
                    .outputVariables(FmuHelper.getOutputArgumentsMetadata(fmu, config.getReturnResultsForEachStep()))
                    .build();
            submodel.getSubmodelElements().add(operation);
        }
        else {
            try {
                LOGGER.debug("reusing existing operation for FMU (FMU reference: {}, operation: {})",
                        ReferenceHelper.asString(fmuReference),
                        ReferenceHelper.asString(EnvironmentHelper.asReference(operation, submodel)));
            }
            catch (AmbiguousElementException e) {
                LOGGER.debug("reusing existing operation for FMU - failed to compute new operation reference (FMU reference: {})",
                        ReferenceHelper.asString(fmuReference));
            }
        }
        assetConnectionManager.registerLambdaOperationProvider(
                ReferenceBuilder.forSubmodel(submodel, operation),
                LambdaOperationProvider.builder()
                        .handle(LambdaExceptionHelper.rethrowBiFunction((OperationVariable[] input, OperationVariable[] inoutput) -> {
                            return handleRunSimulationOperation(fmu, initalParameters, input, inoutput);
                        }))
                        .build());
    }


    private OperationVariable[] handleRunSimulationOperation(Fmu fmu, Map<String, String> initialParameters, OperationVariable[] input, OperationVariable[] inoutput)
            throws IOException {
        int stepCount = Integer.parseInt(requireArgument(input, ARG_STEP_COUNT_ID, DataTypeDefXsd.INTEGER));
        double t = Double.parseDouble(optionalArgument(inoutput, ARG_CURRENT_TIME_ID, DataTypeDefXsd.DOUBLE).orElse("0"));
        double dt = Double.parseDouble(requireArgument(input, ARG_TIME_STEP_ID, DataTypeDefXsd.DOUBLE));
        Map<Integer, List<OperationVariable>> multiStepInput = parseMultiStepInput(input);
        CoSimulationSlave fmuInstance = FmuHelper.createInstance(UUID.randomUUID().toString(), fmu, initialParameters);
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
        if (config.getReturnResultsForEachStep()) {
            return new OperationVariable[] {
                    new DefaultOperationVariable.Builder()
                            .value(resultList)
                            .build()
            };
        }
        return FmuHelper.getOutputArgumentsWithValues(fmuInstance).toArray(OperationVariable[]::new);
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
                        || !SubmodelElementCollection.class.isInstance(argumentsPerStep)) {
                    continue;
                }
                SubmodelElementCollection argumentsCollection = (SubmodelElementCollection) argumentsPerStep;
                Optional<Property> stepNumber = argumentsCollection.getValue().stream()
                        .filter(Objects::nonNull)
                        .filter(x -> Objects.equals(ARG_STEP_NUMBER_ID, x.getIdShort()))
                        .filter(Property.class::isInstance)
                        .map(Property.class::cast)
                        .filter(x -> Objects.equals(DataTypeDefXsd.INTEGER, x.getValueType()))
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
                        .typeValueListElement(AasSubmodelElements.SUBMODEL_ELEMENT_COLLECTION)
                        .value(new DefaultSubmodelElementCollection.Builder()
                                .value(
                                        Stream.concat(
                                                Stream.of(new DefaultProperty.Builder()
                                                        .idShort(ARG_STEP_NUMBER_ID)
                                                        .valueType(DataTypeDefXsd.INTEGER)
                                                        .build()),
                                                originalArgs.stream().map(OperationVariable::getValue))
                                                .toList())
                                .qualifiers(new DefaultQualifier.Builder()
                                        .kind(QualifierKind.TEMPLATE_QUALIFIER)
                                        .valueType(DataTypeDefXsd.STRING)
                                        .value(Cardinality.ZERO_TO_MANY.getNameForSerialization())
                                        .type(Cardinality.class.getSimpleName())
                                        .build())
                                .build())
                        .build())
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
