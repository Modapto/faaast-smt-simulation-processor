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

import de.fraunhofer.iosb.ilt.faaast.service.util.ReferenceBuilder;
import org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd;
import org.eclipse.digitaltwin.aas4j.v3.model.OperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultLangStringTextType;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultOperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultProperty;


/**
 * Constants for SMT Simulation Processor.
 */
public class Constants {
    private Constants() {}

    public static final String EXTENSION_KEY_OPERATION_TO_DIGITAL_FILE_LINK = "smt-simulation:implements";

    public static final Reference SEMANTIC_ID_SMT_SIMULATION = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/SimulationModels/1/0");
    public static final Reference SEMANTIC_ID_SIMULATION_MODEL = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/SimulationModel/1/0");
    public static final Reference SEMANTIC_ID_MODEL_FILE = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/ModelFile/1/0");
    public static final Reference SEMANTIC_ID_MODEL_FILE_VERSION = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/ModelFileVersion/1/0");
    public static final Reference SEMANTIC_ID_DIGITAL_FILE = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/DigitalFile/1/0");
    public static final Reference SEMANTIC_ID_PARAM_FILE = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/ParamFile/1/0");
    public static final Reference SEMANTIC_ID_MODEL_FILE_TYPE = ReferenceBuilder.global("https://admin-shell.io/idta/SimulationModels/ModelFileType/1/0");

    public static final String ID_SHORT_SIMULATION_MODELS = "SimulationModels";
    public static final String ID_SHORT_SIMULATION_MODEL_PARAM_FILE = "ParamFile";
    public static final String ID_SHORT_SIMULATION_MODEL_MODEL_FILE = "ModelFile";
    public static final String ID_SHORT_MODEL_FILE_MODEL_FILE_VERSION = "ModelFileVersion";
    public static final String ID_SHORT_MODEL_FILE_VERSOIN_DIGITAL_FILE = "DigitalFile";

    public static final String ARG_INSTANCE_NAME_ID = "instanceName";
    public static final String ARG_CURRENT_TIME_ID = "currentTime";
    public static final String ARG_TIME_STEP_ID = "timeStep";
    public static final String ARG_STEP_NUMBER_ID = "stepNumber";
    public static final String ARG_STEP_COUNT_ID = "stepCount";
    public static final String ARG_ARGS_PER_STEP_ID = "argumentsPerStep";
    public static final String ARG_RESULT_PER_STEP_ID = "resultPerStep";

    public static final String SMC_SIMULATION_MODELS_PREFIX = "SimulationModel_";

    public static final OperationVariable ARG_CURRENT_TIME = new DefaultOperationVariable.Builder()
            .value(new DefaultProperty.Builder()
                    .idShort(ARG_CURRENT_TIME_ID)
                    .description(new DefaultLangStringTextType.Builder()
                            .language("en")
                            .text("name of the newly created instance")
                            .build())
                    .valueType(DataTypeDefXsd.STRING)
                    .build())
            .build();

    public static final OperationVariable ARG_TIME_STEP = new DefaultOperationVariable.Builder()
            .value(new DefaultProperty.Builder()
                    .idShort(ARG_TIME_STEP_ID)
                    .description(new DefaultLangStringTextType.Builder()
                            .language("en")
                            .text("time step size")
                            .build())
                    .valueType(DataTypeDefXsd.DOUBLE)
                    .build())
            .build();

    public static final OperationVariable ARG_STEP_COUNT = new DefaultOperationVariable.Builder()
            .value(new DefaultProperty.Builder()
                    .idShort(ARG_STEP_COUNT_ID)
                    .description(new DefaultLangStringTextType.Builder()
                            .language("en")
                            .text("number of steps to execute")
                            .build())
                    .valueType(DataTypeDefXsd.INTEGER)
                    .build())
            .build();
}
