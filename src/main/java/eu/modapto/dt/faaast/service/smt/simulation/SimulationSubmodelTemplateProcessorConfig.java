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

import de.fraunhofer.iosb.ilt.faaast.service.submodeltemplate.SubmodelTemplateProcessorConfig;
import org.eclipse.digitaltwin.aas4j.v3.model.builder.ExtendableBuilder;


/**
 * Configuration class for {@link SimulationSubmodelTemplateProcessor}.
 */
public class SimulationSubmodelTemplateProcessorConfig extends SubmodelTemplateProcessorConfig<SimulationSubmodelTemplateProcessor> {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ExtendableBuilder<SimulationSubmodelTemplateProcessorConfig, Builder> {

        @Override
        protected Builder getSelf() {
            return this;
        }


        @Override
        protected SimulationSubmodelTemplateProcessorConfig newBuildingInstance() {
            return new SimulationSubmodelTemplateProcessorConfig();
        }

    }

}
