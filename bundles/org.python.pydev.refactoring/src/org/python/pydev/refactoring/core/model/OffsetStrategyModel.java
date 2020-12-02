/******************************************************************************
* Copyright (C) 2006-2009  IFS Institute for Software and others
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Original authors:
*     Dennis Hunziker
*     Ueli Kistler
*     Reto Schuettel
*     Robin Stocker
* Contributors:
*     Fabio Zadrozny <fabiofz@gmail.com> - initial implementation
******************************************************************************/
/* 
 * Copyright (C) 2006, 2007  Dennis Hunziker, Ueli Kistler
 * Copyright (C) 2007  Reto Schuettel, Robin Stocker
 *
 * IFS Institute for Software, HSR Rapperswil, Switzerland
 * 
 */

package org.python.pydev.refactoring.core.model;

import org.eclipse.jface.viewers.StructuredSelection;

public class OffsetStrategyModel extends StructuredSelection {

    private int key;

    private String description;

    public OffsetStrategyModel(int key, String description) {
        this.key = key;
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }

    public int getStrategy() {
        return key;
    }

}