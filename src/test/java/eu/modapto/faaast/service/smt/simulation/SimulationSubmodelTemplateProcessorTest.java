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

import static org.mockito.Mockito.when;

import de.fraunhofer.iosb.ilt.faaast.service.Service;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.AssetConnectionManager;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.AssetOperationProvider;
import de.fraunhofer.iosb.ilt.faaast.service.config.CoreConfig;
import de.fraunhofer.iosb.ilt.faaast.service.model.TypedInMemoryFile;
import de.fraunhofer.iosb.ilt.faaast.service.model.api.StatusCode;
import de.fraunhofer.iosb.ilt.faaast.service.model.api.request.submodel.GetFileByPathRequest;
import de.fraunhofer.iosb.ilt.faaast.service.model.api.response.submodel.GetFileByPathResponse;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceBuilder;
import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import eu.modapto.dt.faaast.service.smt.simulation.Constants;
import eu.modapto.dt.faaast.service.smt.simulation.SimulationSubmodelTemplateProcessor;
import eu.modapto.dt.faaast.service.smt.simulation.SimulationSubmodelTemplateProcessorConfig;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd;
import org.eclipse.digitaltwin.aas4j.v3.model.OperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultFile;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultOperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultProperty;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodel;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementList;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;


public class SimulationSubmodelTemplateProcessorTest {

    private static final String PARAM_FILE_ID_SHORT = "ParamFile";
    private static final String DIGITAL_FILE_ID_SHORT = "DigitalFile";
    private static final String FMU_BOUNCING_BALL = "/BouncingBall.fmu";
    private static final String PROPERTIES_BOUNCING_BALL = "/BouncingBall.properties";

    private static final Reference runSimulationOperationRef = ReferenceBuilder.forSubmodel(
            submodel().getId(),
            "SimulationModel01");

    private static final List<OperationVariable> DEFAULT_INPUT = List.of(
            new DefaultOperationVariable.Builder()
                    .value(new DefaultProperty.Builder()
                            .idShort("currentTime")
                            .value("0")
                            .valueType(DataTypeDefXsd.DOUBLE)
                            .build())
                    .build(),
            new DefaultOperationVariable.Builder()
                    .value(new DefaultProperty.Builder()
                            .idShort("timeStep")
                            .value("0.01")
                            .valueType(DataTypeDefXsd.DOUBLE)
                            .build())
                    .build(),
            new DefaultOperationVariable.Builder()
                    .value(new DefaultProperty.Builder()
                            .idShort("stepCount")
                            .value("3")
                            .valueType(DataTypeDefXsd.INTEGER)
                            .build())
                    .build());

    private void testInvokeOperation(String fmuFile,
                                     String initialParametersFile,
                                     SimulationSubmodelTemplateProcessorConfig config,
                                     List<OperationVariable> input,
                                     List<OperationVariable> expectedOutput)
            throws Exception {

        byte[] fmu = SimulationSubmodelTemplateProcessorTest.class.getResourceAsStream(fmuFile).readAllBytes();
        byte[] initialParameters = StringHelper.isEmpty(initialParametersFile)
                ? new byte[0]
                : SimulationSubmodelTemplateProcessorTest.class.getResourceAsStream(initialParametersFile).readAllBytes();

        //initialize mocks
        SimulationSubmodelTemplateProcessor processor = new SimulationSubmodelTemplateProcessor();
        Service service = Mockito.mock(Service.class);
        AssetConnectionManager assetConnectionManager = new AssetConnectionManager(CoreConfig.DEFAULT, List.of(), service);
        processor.init(CoreConfig.DEFAULT, config, service);
        when(service.execute(Mockito.any(GetFileByPathRequest.class)))
                .thenAnswer(invocation -> {
                    GetFileByPathRequest req = invocation.getArgument(0);
                    if (req.getPath().endsWith(PARAM_FILE_ID_SHORT)) {
                        return GetFileByPathResponse.builder()
                                .statusCode(StatusCode.SUCCESS)
                                .payload(new TypedInMemoryFile.Builder()
                                        .content(initialParameters)
                                        .build())
                                .build();
                    }
                    if (req.getPath().endsWith(DIGITAL_FILE_ID_SHORT)) {
                        return GetFileByPathResponse.builder()
                                .statusCode(StatusCode.SUCCESS)
                                .payload(new TypedInMemoryFile.Builder()
                                        .content(fmu)
                                        .build())
                                .build();
                    }
                    else {
                        return GetFileByPathResponse.builder()
                                .statusCode(StatusCode.CLIENT_ERROR_RESOURCE_NOT_FOUND)
                                .build();
                    }
                });
        // process submodel
        processor.process(submodel(), assetConnectionManager);
        // invoke operation
        AssetOperationProvider operationProvider = Failsafe.with(RetryPolicy.builder()
                .handleResultIf(Objects::isNull)
                .withDelay(Duration.ofMillis(100))
                .withMaxDuration(Duration.ofSeconds(10))
                .build())
                .get(() -> assetConnectionManager.getOperationProvider(runSimulationOperationRef));
        OperationVariable[] actual = operationProvider.invoke(input.toArray(OperationVariable[]::new), new OperationVariable[] {});
        Assert.assertArrayEquals(expectedOutput.toArray(OperationVariable[]::new), actual);
    }


    @Test
    public void testBouncingBall_NoProperties_ResultsPerStep() throws Exception {
        testInvokeOperation(
                FMU_BOUNCING_BALL,
                null,
                SimulationSubmodelTemplateProcessorConfig.builder()
                        .returnResultsForEachStep(true)
                        .build(),
                DEFAULT_INPUT,
                List.of(
                        new DefaultOperationVariable.Builder()
                                .value(new DefaultSubmodelElementList.Builder()
                                        .idShort("resultPerStep")
                                        .value(new DefaultSubmodelElementCollection.Builder()
                                                .value(new DefaultProperty.Builder()
                                                        .idShort("stepNumber")
                                                        .valueType(DataTypeDefXsd.INTEGER)
                                                        .value("1")
                                                        .build())
                                                .value(new DefaultProperty.Builder()
                                                        .idShort("h")
                                                        .valueType(DataTypeDefXsd.DOUBLE)
                                                        .value("0.99955855")
                                                        .build())
                                                .value(new DefaultProperty.Builder()
                                                        .idShort("v")
                                                        .valueType(DataTypeDefXsd.DOUBLE)
                                                        .value("-0.0981")
                                                        .build())
                                                .build())
                                        .value(new DefaultSubmodelElementCollection.Builder()
                                                .value(new DefaultProperty.Builder()
                                                        .idShort("stepNumber")
                                                        .valueType(DataTypeDefXsd.INTEGER)
                                                        .value("2")
                                                        .build())
                                                .value(new DefaultProperty.Builder()
                                                        .idShort("h")
                                                        .valueType(DataTypeDefXsd.DOUBLE)
                                                        .value("0.9981361000000002")
                                                        .build())
                                                .value(new DefaultProperty.Builder()
                                                        .idShort("v")
                                                        .valueType(DataTypeDefXsd.DOUBLE)
                                                        .value("-0.1962000000000001")
                                                        .build())
                                                .build())
                                        .value(new DefaultSubmodelElementCollection.Builder()
                                                .value(new DefaultProperty.Builder()
                                                        .idShort("stepNumber")
                                                        .valueType(DataTypeDefXsd.INTEGER)
                                                        .value("3")
                                                        .build())
                                                .value(new DefaultProperty.Builder()
                                                        .idShort("h")
                                                        .valueType(DataTypeDefXsd.DOUBLE)
                                                        .value("0.9957326500000004")
                                                        .build())
                                                .value(new DefaultProperty.Builder()
                                                        .idShort("v")
                                                        .valueType(DataTypeDefXsd.DOUBLE)
                                                        .value("-0.2943000000000001")
                                                        .build())
                                                .build())
                                        .build())
                                .build()));
    }


    @Test
    public void testBouncingBall_NoProperties_OnlyFinalResult() throws Exception {
        testInvokeOperation(
                FMU_BOUNCING_BALL,
                null,
                SimulationSubmodelTemplateProcessorConfig.builder()
                        .returnResultsForEachStep(false)
                        .build(),
                DEFAULT_INPUT,
                List.of(
                        new DefaultOperationVariable.Builder()
                                .value(new DefaultProperty.Builder()
                                        .idShort("h")
                                        .valueType(DataTypeDefXsd.DOUBLE)
                                        .value("0.9957326500000004")
                                        .build())
                                .build(),
                        new DefaultOperationVariable.Builder()
                                .value(new DefaultProperty.Builder()
                                        .idShort("v")
                                        .valueType(DataTypeDefXsd.DOUBLE)
                                        .value("-0.2943000000000001")
                                        .build())
                                .build()));
    }


    @Test
    public void testBouncingBall_WithProperties_OnlyFinalResult() throws Exception {
        testInvokeOperation(
                FMU_BOUNCING_BALL,
                PROPERTIES_BOUNCING_BALL,
                SimulationSubmodelTemplateProcessorConfig.builder()
                        .returnResultsForEachStep(false)
                        .build(),
                DEFAULT_INPUT,
                List.of(
                        new DefaultOperationVariable.Builder()
                                .value(new DefaultProperty.Builder()
                                        .idShort("h")
                                        .valueType(DataTypeDefXsd.DOUBLE)
                                        .value("0.9913000000000001")
                                        .build())
                                .build(),
                        new DefaultOperationVariable.Builder()
                                .value(new DefaultProperty.Builder()
                                        .idShort("v")
                                        .valueType(DataTypeDefXsd.DOUBLE)
                                        .value("-0.6000000000000002")
                                        .build())
                                .build()));

    }


    private static Submodel submodel() {
        return new DefaultSubmodel.Builder()
                .idShort("SimulationModels")
                .id("http://example.com/submodels/1")
                .semanticId(Constants.SEMANTIC_ID_SMT_SIMULATION)
                .submodelElements(new DefaultSubmodelElementCollection.Builder()
                        .idShort("SimulationModel01")
                        .semanticId(Constants.SEMANTIC_ID_SIMULATION_MODEL)
                        .value(new DefaultSubmodelElementCollection.Builder()
                                .idShort("ModelFile")
                                .semanticId(Constants.SEMANTIC_ID_MODEL_FILE)
                                .value(new DefaultProperty.Builder()
                                        .idShort("ModelFileType")
                                        .semanticId(Constants.SEMANTIC_ID_MODEL_FILE_TYPE)
                                        .valueType(DataTypeDefXsd.STRING)
                                        .value("FMI2.0")
                                        .build())
                                .value(new DefaultSubmodelElementCollection.Builder()
                                        .idShort("ModelFileVersion01")
                                        .semanticId(Constants.SEMANTIC_ID_MODEL_FILE_VERSION)
                                        .value(new DefaultFile.Builder()
                                                .idShort("DigitalFile")
                                                .semanticId(Constants.SEMANTIC_ID_DIGITAL_FILE)
                                                .value("/aasx/files/fmu.fmu")
                                                .contentType("application/octet-stream")
                                                .build())
                                        .build())
                                .build())
                        .value(new DefaultFile.Builder()
                                .idShort("ParamFile")
                                .semanticId(Constants.SEMANTIC_ID_PARAM_FILE)
                                .value("/aasx/files/init-params.properties")
                                .contentType("text/plain")
                                .build())
                        .build())
                .build();
    }

}
