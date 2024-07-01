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

import de.fraunhofer.iosb.ilt.faaast.service.ServiceContext;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.AssetConnectionException;
import de.fraunhofer.iosb.ilt.faaast.service.assetconnection.AssetConnectionManager;
import de.fraunhofer.iosb.ilt.faaast.service.config.CoreConfig;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.DeserializationException;
import de.fraunhofer.iosb.ilt.faaast.service.dataformat.environment.deserializer.AasxEnvironmentDeserializer;
import de.fraunhofer.iosb.ilt.faaast.service.exception.ConfigurationException;
import de.fraunhofer.iosb.ilt.faaast.service.exception.ConfigurationInitializationException;
import de.fraunhofer.iosb.ilt.faaast.service.model.EnvironmentContext;
import de.fraunhofer.iosb.ilt.faaast.service.model.TypedInMemoryFile;
import de.fraunhofer.iosb.ilt.faaast.service.model.api.response.submodel.GetFileByPathResponse;
import de.fraunhofer.iosb.ilt.faaast.service.model.exception.ResourceNotFoundException;
import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceBuilder;
import java.io.IOException;
import java.util.List;
import org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd;
import org.eclipse.digitaltwin.aas4j.v3.model.OperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
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
    private static ServiceContext serviceContext;
    private static AssetConnectionManager assetConnectionManager;

    @BeforeClass
    public static void init() throws ConfigurationException, DeserializationException, ResourceNotFoundException {
        processor = new SimulationSubmodelTemplateProcessor();
        serviceContext = Mockito.mock(ServiceContext.class);
        assetConnectionManager = new AssetConnectionManager(CoreConfig.DEFAULT, List.of(), serviceContext);
        processor.init(CoreConfig.DEFAULT, new SimulationSubmodelTemplateProcessorConfig(), serviceContext);
        EnvironmentContext environment = new AasxEnvironmentDeserializer()
                .read(SimulationSubmodelTemplateProcessorTest.class.getResourceAsStream("/aas-with-bouncing-ball.aasx"));

        when(serviceContext.execute(any())).thenReturn(GetFileByPathResponse.builder()
                .payload(new TypedInMemoryFile.Builder()
                        .content(environment.getFiles().get(0).getFileContent())
                        .build())
                .build());
        Submodel submodel = environment.getEnvironment().getSubmodels().get(0);
        processor.process(submodel, assetConnectionManager);
    }

    private static final Reference createInstanceOperationRef = ReferenceBuilder.forSubmodel(
            "https://example.com/ids/sm/4163_9072_2032_6099",
            "SimulationModel01-CreateInstance");;

    private static Reference doStepOperationRef = ReferenceBuilder.forSubmodel(
            "https://example.com/ids/sm/4163_9072_2032_6099",
            "SimulationModel01-DoStep");

    private static Reference runSimulationOperationRef = ReferenceBuilder.forSubmodel(
            "https://example.com/ids/sm/4163_9072_2032_6099",
            "SimulationModel01-RunSimulation");

    @Test
    public void testExecuteDoStep()
            throws ConfigurationInitializationException, ConfigurationException, ResourceNotFoundException, AssetConnectionException, DeserializationException, IOException {

        OperationVariable[] createInstanceResult = assetConnectionManager.getOperationProvider(createInstanceOperationRef).invoke(new OperationVariable[] {},
                new OperationVariable[] {});
        Assert.assertNotNull(createInstanceResult);
        Assert.assertEquals(1, createInstanceResult.length);
        Assert.assertTrue(Property.class.isInstance(createInstanceResult[0].getValue()));

        String instanceName = ((Property) createInstanceResult[0].getValue()).getValue();

        OperationVariable[] doStepExpectedResult = new OperationVariable[] {
                new DefaultOperationVariable.Builder()
                        .value(new DefaultProperty.Builder()
                                .idShort("h")
                                .valueType(DataTypeDefXsd.DOUBLE)
                                .value("0.99955855")
                                .build())
                        .build(),
                new DefaultOperationVariable.Builder()
                        .value(new DefaultProperty.Builder()
                                .idShort("v")
                                .valueType(DataTypeDefXsd.DOUBLE)
                                .value("-0.0981")
                                .build())
                        .build(),
        };
        OperationVariable[] doStepResult = assetConnectionManager.getOperationProvider(doStepOperationRef).invoke(
                new OperationVariable[] {
                        new DefaultOperationVariable.Builder()
                                .value(new DefaultProperty.Builder()
                                        .idShort("instanceName")
                                        .valueType(DataTypeDefXsd.STRING)
                                        .value(instanceName)
                                        .build())
                                .build(),
                        new DefaultOperationVariable.Builder()
                                .value(new DefaultProperty.Builder()
                                        .idShort("currentTime")
                                        .valueType(DataTypeDefXsd.DOUBLE)
                                        .value("0")
                                        .build())
                                .build(),
                        new DefaultOperationVariable.Builder()
                                .value(new DefaultProperty.Builder()
                                        .idShort("timeStep")
                                        .valueType(DataTypeDefXsd.DOUBLE)
                                        .value("0.01")
                                        .build())
                                .build(),
                },
                new OperationVariable[] {});

        Assert.assertArrayEquals(doStepExpectedResult, doStepResult);
    }


    @Test
    public void testExecuteRunSimulation()
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
