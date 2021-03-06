/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.workbench.wi.casemgmt;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.guvnor.common.services.project.categories.Process;
import org.uberfire.backend.vfs.Path;
import org.uberfire.workbench.annotations.VisibleAsset;
import org.uberfire.workbench.category.Category;
import org.uberfire.workbench.type.ResourceTypeDefinition;

@VisibleAsset
@ApplicationScoped
public class CMMNResourceTypeDefinition implements ResourceTypeDefinition {

    private Category category;

    public CMMNResourceTypeDefinition() {
    }

    @Inject
    public CMMNResourceTypeDefinition(final Process category) {
        this.category = category;
    }

    @Override
    public Category getCategory() {
        return this.category;
    }

    @Override
    public String getShortName() {
        return "Case Management Model";
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public String getPrefix() {
        return "";
    }

    @Override
    public String getSuffix() {
        return "cmmn";
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean accept(final Path path) {
        return (path.getFileName().endsWith("." + getSuffix()));
    }

    @Override
    public String getSimpleWildcardPattern() {
        return "*." + getSuffix();
    }
}
