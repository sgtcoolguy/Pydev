/*
 * @author: fabioz
 * Created: February 2004
 * License: Common Public License v1.0
 */

package org.python.pydev.editor.actions.navigation;

import org.python.pydev.outline.ParsedItem;
import org.python.pydev.outline.SelectionPosition;

/**
 * @author Fabio Zadrozny
 */
public class PyNextMethod extends PyMethodNavigation{

	public SelectionPosition getSelect(Visitor v) {
		if (v.nextNode != null){
			return ParsedItem.getPosition(v.nextNode);
		}
		return null;
	}
}
