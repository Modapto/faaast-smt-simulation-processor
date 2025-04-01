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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.fraunhofer.iosb.ilt.faaast.service.Service;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.AssetConnectionException;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.AssetConnectionManager;
import de.fraunhofer.iosb.ilt.faaast.service.config.CoreConfig;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.DeserializationException;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.environment.deserializer.AasxEnvironmentDeserializer;
import de.fraunhofer.iosb.ilt.faaast.service.exception.ConfigurationException;
import de.fraunhofer.iosb.ilt.faaast.service.exception.ConfigurationInitializationException;
import de.fraunhofer.iosb.ilt.faaast.service.model.EnvironmentContext;
import de.fraunhofer.iosb.ilt.faaast.service.model.TypedInMemoryFile;
import de.fraunhofer.iosb.ilt.faaast.service.model.api.StatusCode;
import de.fraunhofer.iosb.ilt.faaast.service.model.api.response.submodel.GetFileByPathResponse;
import de.fraunhofer.iosb.ilt.faaast.service.model.exception.ResourceNotFoundException;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceBuilder;
import eu.modapto.dt.faaast.service.smt.simulation.SimulationSubmodelTemplateProcessor;
import eu.modapto.dt.faaast.service.smt.simulation.SimulationSubmodelTemplateProcessorConfig;
import java.io.IOException;
import java.util.List;
import org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd;
import org.eclipse.digitaltwin.aas4j.v3.model.OperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultOperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultProperty;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementList;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;


public class SimulationSubmodelTemplateProcessorTest {

    private static SimulationSubmodelTemplateProcessor processor;
    private static Service service;
    private static AssetConnectionManager assetConnectionManager;

    @BeforeClass
    public static void init() throws ConfigurationException, DeserializationException, ResourceNotFoundException {
        processor = new SimulationSubmodelTemplateProcessor();
        service = Mockito.mock(Service.class);
        assetConnectionManager = new AssetConnectionManager(CoreConfig.DEFAULT, List.of(), service);
        processor.init(CoreConfig.DEFAULT, new SimulationSubmodelTemplateProcessorConfig(), service);
        EnvironmentContext environment = new AasxEnvironmentDeserializer()
                .read(SimulationSubmodelTemplateProcessorTest.class.getResourceAsStream("/aas-with-bouncing-ball.aasx"));

        when(service.execute(any())).thenReturn(GetFileByPathResponse.builder()
                .statusCode(StatusCode.SUCCESS)
                .payload(new TypedInMemoryFile.Builder()
                        .content(environment.getFiles().get(0).getFileContent())
                        .build())
                .build());
        Submodel submodel = environment.getEnvironment().getSubmodels().get(0);
        processor.process(submodel, assetConnectionManager);
    }

    private static final Reference runSimulationOperationRef = ReferenceBuilder.forSubmodel(
            "https://example.com/ids/sm/4163_9072_2032_6099",
            "SimulationModel01");

    @Test
    public void testExecuteSimulation()
            throws ConfigurationInitializationException, ConfigurationException, ResourceNotFoundException, AssetConnectionException, DeserializationException, IOException {

        OperationVariable[] input = new OperationVariable[] {
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
                        .build()
        };

        OperationVariable[] actual = assetConnectionManager
                .getOperationProvider(runSimulationOperationRef)
                .invoke(input, new OperationVariable[] {});

        OperationVariable[] expected = new OperationVariable[] {
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
                        .build()
        };
        Assert.assertArrayEquals(expected, actual);
    }

}
