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

import static eu.modapto.dt.faaast.service.smt.simulation.Constants.ARG_RESULT_PER_STEP_ID;
import static eu.modapto.dt.faaast.service.smt.simulation.Constants.ARG_STEP_NUMBER_ID;
import static no.ntnu.ihb.fmi4j.FmiStatus.Pending;
import static no.ntnu.ihb.fmi4j.modeldescription.variables.VariableType.STRING;
import static org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd.BOOLEAN;
import static org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd.INTEGER;
import static org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd.STRING;

import de.fraunhofer.iosb.ilt.faaast.service.util.Ensure;
import de.fraunhofer.iosb.ilt.faaast.service.util.StringHelper;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.ntnu.ihb.fmi4j.Fmi4jVariableUtils;
import no.ntnu.ihb.fmi4j.FmiStatus;
import no.ntnu.ihb.fmi4j.VariableRead;
import no.ntnu.ihb.fmi4j.importer.fmi2.CoSimulationSlave;
import no.ntnu.ihb.fmi4j.importer.fmi2.FmiStatusKind;
import no.ntnu.ihb.fmi4j.importer.fmi2.Fmu;
import no.ntnu.ihb.fmi4j.modeldescription.variables.BooleanVariable;
import no.ntnu.ihb.fmi4j.modeldescription.variables.Causality;
import no.ntnu.ihb.fmi4j.modeldescription.variables.IntegerVariable;
import no.ntnu.ihb.fmi4j.modeldescription.variables.ModelVariables;
import no.ntnu.ihb.fmi4j.modeldescription.variables.RealVariable;
import no.ntnu.ihb.fmi4j.modeldescription.variables.StringVariable;
import no.ntnu.ihb.fmi4j.modeldescription.variables.TypedScalarVariable;
import no.ntnu.ihb.fmi4j.modeldescription.variables.Variability;
import no.ntnu.ihb.fmi4j.modeldescription.variables.VariableType;
import org.eclipse.digitaltwin.aas4j.v3.model.AasSubmodelElements;
import org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd;
import org.eclipse.digitaltwin.aas4j.v3.model.OperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultLangStringTextType;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultOperationVariable;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultProperty;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementCollection;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelElementList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Helper class to work with FMU models and files.
 *
 * @see <a href="https://fmi-standard.org/">https://fmi-standard.org/</a>
 */
public class FmuHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(FmuHelper.class);

    /**
     * Gets the output arguments with current values from the fmuInstance.
     *
     * @param fmuInstance the FMU instance to read the values from
     * @return the output variables of the FMU as AAS arguments to return as result of an operation
     */
    public static List<OperationVariable> getOutputArgumentsWithValues(CoSimulationSlave fmuInstance) {
        return fmuInstance.getModelVariables().getByCausality(Causality.OUTPUT).stream()
                .map(x -> (OperationVariable) new DefaultOperationVariable.Builder()
                        .value(new DefaultProperty.Builder()
                                .idShort(x.getName())
                                .valueType(asAasDatatype(x.getType()))
                                .value(readAsAasValue(x, fmuInstance))
                                .build())
                        .build())
                .collect(Collectors.toList());
    }


    private static void initializeModelParameters(CoSimulationSlave instance, Map<String, String> initialParameters) {
        if (Objects.isNull(initialParameters) || initialParameters.isEmpty()) {
            return;
        }
        List<TypedScalarVariable<?>> modelParameters = instance.getModelVariables().getVariables().stream()
                .filter(x -> x.getCausality() == Causality.PARAMETER)
                .filter(x -> x.getVariability() == Variability.FIXED || x.getVariability() == Variability.TUNABLE)
                .sorted(Comparator.comparing(TypedScalarVariable::getName))
                .toList();
        List<String> matchedParameters = new ArrayList<>();
        modelParameters.forEach(x -> {
            if (initialParameters.containsKey(x.getName())) {
                LOGGER.debug("{} --> {}", x.getName(), initialParameters.get(x.getName()));
                FmuHelper.setVariable(instance, x.getName(), initialParameters.get(x.getName()));
                matchedParameters.add(x.getName());
            }
            else {
                LOGGER.debug("{} = {} (default)", x.getName(), x.getStart());
            }
        });
        initialParameters.keySet().stream()
                .sorted()
                .filter(x -> !matchedParameters.contains(x))
                .forEach(x -> LOGGER.warn("parameter does not exist: {}", x));
        if (modelParameters.isEmpty() && initialParameters.isEmpty()) {
            LOGGER.debug("   [model does not have any parameters]");
        }
    }


    /**
     * Creates an instance a the fmu.
     *
     * @param name name of the instance
     * @param fmu the FMU
     * @param initialParameters initial parameters to set before initialization
     * @return new FMU instance with initial arguments
     */
    public static CoSimulationSlave createInstance(String name, Fmu fmu, Map<String, String> initialParameters) {
        LOGGER.debug("creating new FMU instance... (name: {})", name);
        CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance();
        LOGGER.debug("initializing new FMU instance... (name: {})", name);
        if (!instance.setupExperiment(0, 0, 0)) {
            LOGGER.warn("setupExperiment failed");
        }
        initializeModelParameters(instance, initialParameters);
        if (!instance.enterInitializationMode()) {
            LOGGER.warn("enterInitializationModel failed");
        }
        if (!instance.exitInitializationMode()) {
            LOGGER.warn("exitInitializationModel failed");
        }
        return instance;
    }


    /**
     * Reads input arguments from a FMU and converts them to AAS arguments. This method reads only metadata such as name and
     * datatype but not the actual value.
     *
     * @param fmu the FMU
     * @return the input arguments in AAS metamodel
     */
    public static List<OperationVariable> getInputArgumentsMetadata(Fmu fmu) {
        return getArgumentsByCausality(fmu, Causality.INPUT, Causality.OUTPUT).stream()
                .map(FmuHelper::asOperationVariable)
                .toList();
    }


    /**
     * Reads inoutput arguments from a FMU and converts them to AAS arguments. This method reads only metadata such as name
     * and datatype but not the actual value.
     *
     * @param fmu the FMU
     * @return the inoutput arguments in AAS metamodel
     */
    public static List<OperationVariable> getInoutputArgumentsMetadata(Fmu fmu) {
        ModelVariables modelVariables = fmu.getModelDescription().getModelVariables();
        List<TypedScalarVariable<?>> input = modelVariables.getByCausality(Causality.INPUT);
        List<TypedScalarVariable<?>> output = modelVariables.getByCausality(Causality.OUTPUT);
        return input.stream()
                .filter(x -> output.stream()
                        .anyMatch(y -> Objects.equals(x.getName(), y.getName())
                                && Objects.equals(x.getType(), y.getType())))
                .map(FmuHelper::asOperationVariable)
                .collect(Collectors.toList());
    }


    /**
     * Gets the output arguments definition for an AAS operation.
     *
     * @param fmu the FMU
     * @param returnResultsForEachStep if results should be returned for each step or only the last one
     * @return list of operation variable describing the result of the AAS operation
     */
    public static List<OperationVariable> getOutputArgumentsMetadata(Fmu fmu, boolean returnResultsForEachStep) {
        List<OperationVariable> result = getArgumentsByCausality(fmu, Causality.OUTPUT, Causality.INPUT).stream()
                .map(FmuHelper::asOperationVariable)
                .toList();
        if (!returnResultsForEachStep) {
            return result;
        }
        return List.of(new DefaultOperationVariable.Builder()
                .value(new DefaultSubmodelElementList.Builder()
                        .idShort(ARG_RESULT_PER_STEP_ID)
                        .typeValueListElement(AasSubmodelElements.SUBMODEL_ELEMENT_COLLECTION)
                        .value(new DefaultSubmodelElementCollection.Builder()
                                .value(
                                        Stream.concat(
                                                Stream.of(new DefaultProperty.Builder()
                                                        .idShort(ARG_STEP_NUMBER_ID)
                                                        .valueType(DataTypeDefXsd.INTEGER)
                                                        .build()),
                                                result.stream().map(OperationVariable::getValue))
                                                .toList())
                                .build())
                        .build())
                .build());
    }


    /**
     * Loads a FMU from byte[].
     *
     * @param name name of the FMU
     * @param fmuBinary binary content containing the FMU
     * @return the loaded FMU
     * @throws IOException if loading fails
     */
    public static Fmu loadFmu(String name, byte[] fmuBinary) throws IOException {
        return Fmu.Companion.from(name, fmuBinary);
    }


    /**
     * Loads a FMU from byte[] with random generated name.
     *
     * @param fmuBinary binary content containing the FMU
     * @return the loaded FMU
     * @throws IOException if loading fails
     */
    public static Fmu loadFmu(byte[] fmuBinary) throws IOException {
        return loadFmu(UUID.randomUUID().toString(), fmuBinary);
    }


    private static void checkFmuStatus(FmiStatus status, String errorMessage) throws FmuException {
        checkFmuStatus(null, status, errorMessage);
    }


    private static void checkFmuStatus(CoSimulationSlave instance, FmiStatus status, String errorMessage) throws FmuException {
        switch (status) {
            case NONE:
            case OK: {
                return;
            }

            case Warning: {
                LOGGER.debug("Received FMU status '{}' - {}", status, errorMessage);
                return;
            }
            case Discard:
            case Error:
            case Fatal: {
                throw new FmuException(String.format("Received FMU status '%s' - %s", status, errorMessage));
            }
            case Pending: {
                if (Objects.isNull(instance)) {
                    LOGGER.warn("execution of doStep() is pending, but missing reference to co-simulation instance to wait for finish - result may be inaccurate/wrong");
                    return;
                }
                try {
                    checkFmuStatus(Failsafe.with(
                            RetryPolicy.builder()
                                    .handleResultIf(x -> Pending == x)
                                    .withDelay(Duration.ofMillis(100))
                                    .withMaxDuration(Duration.ofSeconds(10))
                                    .build())
                            .get(() -> instance.getStatus(FmiStatusKind.DO_STEP_STATUS)),
                            String.format("encountered pending state for doStep that has been resolved (message: %s)", errorMessage));
                }
                catch (Exception e) {
                    throw new FmuException("doStep() returned pending for over 10 seconds");
                }
            }
        }
    }


    private static void ensureWritable(TypedScalarVariable<?> variable) {
        Ensure.requireNonNull(variable, "variable must be non-null");
        switch (variable.getCausality()) {
            case INPUT:
            case PARAMETER:
                return;
            default:
                throw new FmuException(String.format("error writing to variable - unsupported causality (causality: %s)", variable.getCausality()));
        }
    }


    /**
     * Set a variable in FMU to given value.
     *
     * @param instance the FMU instance to set the value
     * @param name name of the variable
     * @param value the value to set
     */
    public static void setVariable(CoSimulationSlave instance, String name, String value) {
        TypedScalarVariable fmuVariable = null;
        try {
            fmuVariable = instance.getModelDescription().getVariableByName(name);
        }
        catch (IllegalArgumentException e) {
            throw new FmuException(String.format("failed to set variable - supported datatype (name: %s, value: %s, datatype: %s)", name, value, fmuVariable.getClass().getName()));
        }
        ensureWritable(fmuVariable);
        FmiStatus status;
        if (fmuVariable instanceof IntegerVariable) {
            status = instance.writeInteger(
                    new long[] {
                            fmuVariable.getValueReference()
                    },
                    new int[] {
                            (int) Double.parseDouble(value)
                    });
        }
        else if (fmuVariable instanceof BooleanVariable) {
            status = instance.writeBoolean(
                    new long[] {
                            fmuVariable.getValueReference()
                    },
                    new boolean[] {
                            Objects.isNull(value)
                                    ? false
                                    : Objects.equals("1", value)
                                            ? true
                                            : Boolean.parseBoolean(value)
                    });
        }
        else if (fmuVariable instanceof RealVariable) {
            status = instance.writeReal(
                    new long[] {
                            fmuVariable.getValueReference()
                    },
                    new double[] {
                            Double.parseDouble(value)
                    });
        }
        else if (fmuVariable instanceof StringVariable) {
            status = instance.writeString(
                    new long[] {
                            fmuVariable.getValueReference()
                    },
                    new String[] {
                            value
                    });
        }
        else {
            throw new FmuException(String.format("failed to set variable - supported datatype (name: %s, value: %s, datatype: %s)", name, value, fmuVariable.getClass().getName()));
        }
        checkFmuStatus(status, String.format("failed to set variable (name: %s, value: %s)", name, value));
    }


    /**
     * Sets the current value of FMU input variables from AAS metamodel. Values in input that are not present as input
     * variables in the FMU are ignored.
     *
     * @param instance the FMU instance
     * @param input the AAS input variables containing the values to set
     */
    public static void setFmuInputVariablesFromAas(CoSimulationSlave instance, List<OperationVariable> input) {
        if (Objects.isNull(input)) {
            return;
        }
        input.stream()
                .map(OperationVariable::getValue)
                .filter(Property.class::isInstance)
                .map(Property.class::cast)
                .forEach(x -> FmuHelper.setVariable(instance, x.getIdShort(), x.getValue()));
    }


    /**
     * Converts datatypes from FMU to AAS.
     *
     * @param fmuType the FMU datatye to convert
     * @return the corresponding AAS datatype
     * @throws IllegalArgumentException if conversion fails, e.g., because there is no type mapping
     */
    public static DataTypeDefXsd asAasDatatype(VariableType fmuType) {
        switch (fmuType) {
            case STRING -> {
                return DataTypeDefXsd.STRING;
            }
            case BOOLEAN -> {
                return DataTypeDefXsd.BOOLEAN;
            }
            case INTEGER -> {
                return DataTypeDefXsd.INTEGER;
            }
            case REAL -> {
                return DataTypeDefXsd.DOUBLE;
            }
            default -> throw new IllegalArgumentException(String.format("unsupported FMU datatype: %s", fmuType));
        }
    }


    /**
     * Reads the value of a variable from the FMU instance and ensures conformity with AAS datatypes. This especially
     * includes converting boolean values of 0 and 1 to false and true.
     *
     * @param variable the variable to read
     * @param fmuInstance the FMU instance to read from
     * @return the value of the variable as AAS-compliant string
     */
    public static String readAsAasValue(TypedScalarVariable<?> variable, CoSimulationSlave fmuInstance) {
        VariableRead<?> readResult = Fmi4jVariableUtils.read(variable, fmuInstance);
        checkFmuStatus(readResult.getStatus(), String.format("failed to read variable from FMU (name: %s, type: %s)", variable.getName(), variable.getType()));
        String value = Objects.toString(readResult.getValue(), "");
        if (variable.getType() == VariableType.BOOLEAN) {
            value = StringHelper.isEmpty(value)
                    ? "false"
                    : Objects.equals("1", value)
                            ? "true"
                            : Boolean.toString(Boolean.parseBoolean(value));
        }
        return value;
    }


    /**
     * Converts an FMU variable to an AAS OperationVariable without the actual value from FMU.
     *
     * @param variable the FMU variable to convert
     * @return the AAS OperationVariable.
     */
    public static OperationVariable asOperationVariable(TypedScalarVariable<?> variable) {
        return new DefaultOperationVariable.Builder()
                .value(new DefaultProperty.Builder()
                        .idShort(variable.getName())
                        .description(new DefaultLangStringTextType.Builder()
                                .language("en")
                                .text(variable.getDescription())
                                .build())
                        .valueType(asAasDatatype(variable.getType()))
                        .build())
                .build();
    }


    /**
     * Gets arguments from an FMU based on causality.
     *
     * @param fmu the FMU
     * @param causality the causality to include
     * @param excludedCausality the causality to exclude
     * @return list of FMU variables for the matching causality
     */
    public static List<TypedScalarVariable<?>> getArgumentsByCausality(Fmu fmu, Causality causality, Causality excludedCausality) {
        ModelVariables modelVariables = fmu.getModelDescription().getModelVariables();
        List<TypedScalarVariable<?>> included = modelVariables.getByCausality(causality);
        List<TypedScalarVariable<?>> excluded = modelVariables.getByCausality(excludedCausality);
        return included.stream()
                .filter(x -> excluded.stream()
                        .noneMatch(y -> Objects.equals(x.getName(), y.getName())
                                && Objects.equals(x.getType(), y.getType())))
                .collect(Collectors.toList());
    }
}
